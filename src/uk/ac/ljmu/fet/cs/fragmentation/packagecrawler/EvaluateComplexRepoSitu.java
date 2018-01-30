/*
 *  ========================================================================
 *  Fragmentation Experiments
 *  ========================================================================
 *  
 *  This file is part of Fragmentation experiments.
 *  
 *  Fragmentation Experiments is free software: you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *  
 *  Fragmentation Experiments is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with Fragmentation Experiments.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 */

package uk.ac.ljmu.fet.cs.fragmentation.packagecrawler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Sets;

import uk.ac.ljmu.fet.cs.fragmentation.packagecrawler.ConstructionPath.SimplePath;

// Detects the fragmentation possibilities according to ENTICE policies
// See the paper titled "ENTICE VM image analysis and optimised fragmentation" for details
// To configure this program, you can use a text file. 
//   A sample is available in the root folder of this repository. It is called "reposetup".
public class EvaluateComplexRepoSitu {
	public static final String PACKAGES_IN_COMBINATION = "Packages in combination:";
	public static final String TOTAL_SIZE = "Total size:";
	public static final String CHILDREN_COMB_IDS = "Children comb ids:";
	public static final String PARENT_FRAGMENT_IDS = "Parent fragment ids:";
	public static final String CURRENT_FRAGMENT_ID = "Current fragment id:";
	public static final String START_OF_FRAGMENT = "START OF FRAGMENT DESCRIPTION";
	public static final String END_OF_FRAGMENT = "END OF FRAGMENT DESCRIPTION";
	public static final long keeperThreshold = 1024 * 1024 * 10;

	public static enum ConfigMode {
		Pre, Config, Contents
	}

	public static ArrayList<Package> base;
	public static ArrayList<ArrayList<Package>> topcontents = new ArrayList<>();
	public static ArrayList<ConstructionPath> howToBuildTopContents = new ArrayList<>();
	public static ArrayList<Set<Package>> minPathsToTopContents = new ArrayList<>();
	public static HashMap<String, HashSet<Set<String>>> reverseCombs = new HashMap<>();

	public static void loadSetup(String configFileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(configFileName));
		String line;
		ConfigMode currentMode = ConfigMode.Pre;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty()) {
				continue;
			}
			if (line.startsWith("[")) {
				line = line.substring(1).split("]")[0];
				currentMode = ConfigMode.valueOf(line);
				continue;
			}
			String[] frags;
			switch (currentMode) {
			case Config:
				frags = line.split("=");
				if (frags[0].equals("packagecache")) {
					Package.crawlPackageFolder(frags[1]);
					base = new ArrayList<>(Package.essentials);
				} else if (frags[0].equals("basepackagelist")) {
					if (Package.completeList.isEmpty()) {
						System.err.println(
								"Tried to load base package list before scanning package cache for packages...");
						System.exit(3);
					}
					base = Package.loadDPKGList(frags[1]);
				}
				break;
			case Contents:
				frags = line.split("\\s+");
				ArrayList<Package> pkgSet = new ArrayList<>();
				for (String frag : frags) {
					Package p = Package.completeList.get(frag);
					if (p == null) {
						System.err.println("Unknown package: " + frag);
						System.exit(2);
					}
					pkgSet.add(p);
				}
				topcontents.add(pkgSet);
				break;
			default:
				System.err.println("Unexpected config ...");
				System.exit(1);
			}
		}
		br.close();
	}

	@SuppressWarnings("unchecked")
	public static HashMap<Set<String>, Set<Package>> combineAllIn(HashMap<Set<String>, Set<Package>> in) {
		long before = System.currentTimeMillis();
		HashMap<Set<String>, Set<Package>> combinations = new HashMap<>(in.size());
		ArrayList<HashMap<Set<String>, Set<Package>>> allcombinations = new ArrayList<>();
		Set<String>[] keysofIn = in.keySet().toArray(new Set[0]);
		for (int i = 0; i < keysofIn.length; i++) {
			System.err.print(".");
			for (int j = i + 1; j < keysofIn.length; j++) {
				// The actual combination calculation
				Set<String> keyFirst = keysofIn[i];
				Set<String> keySecond = keysofIn[j];
				Set<Package> first = in.get(keyFirst);
				Set<Package> second = in.get(keySecond);
				boolean useFirst = first.size() < second.size();
				Set<Package> a = Sets.newTreeSet(useFirst ? first : second);
				a.retainAll(useFirst ? second : first);
				if (a.size() > 0) {
					useFirst = keyFirst.size() > keySecond.size();
					Set<String> currComb = Sets.newHashSet(useFirst ? keyFirst : keySecond);
					currComb.addAll(useFirst ? keySecond : keyFirst);
					combinations.put(currComb, a);
				}
			}
			if (i % 10 == 0) {
				cleanUpReoccuringCombinations(combinations);
				allcombinations.add(combinations);
				combinations = new HashMap<>();
			}
		}
		for (HashMap<Set<String>, Set<Package>> combSubSet : allcombinations) {
			combinations.putAll(combSubSet);
		}
		cleanUpReoccuringCombinations(combinations);
		System.err.println();
		System.err.println("Combination took " + (System.currentTimeMillis() - before) + "ms");
		return combinations;
	}

	private static void cleanUpReoccuringCombinations(HashMap<Set<String>, Set<Package>> allCombinations) {
		for (final Set<String> key : allCombinations.keySet()) {
			String pkgCSVList = csvPkgNameList(allCombinations.get(key));
			HashSet<Set<String>> matchingKeys = reverseCombs.get(pkgCSVList);
			if (matchingKeys == null) {
				matchingKeys = new HashSet<>();
				reverseCombs.put(pkgCSVList, matchingKeys);
			}
			matchingKeys.add(key);
		}
		for (final String reverseKey : reverseCombs.keySet()) {
			HashSet<Set<String>> keyList = reverseCombs.get(reverseKey);
			if (keyList.size() > 1) {
				Set<String> newKey = new HashSet<>();
				Set<Package> pkgs = null;
				for (Set<String> obsoleteKey : keyList) {
					Set<Package> underRemoval = allCombinations.remove(obsoleteKey);
					pkgs = underRemoval == null ? pkgs : underRemoval;
					newKey.addAll(obsoleteKey);
				}
				allCombinations.put(newKey, pkgs);
				keyList.clear();
				keyList.add(newKey);
			}
		}
	}

	private static String csvPkgNameList(Collection<Package> pkgs) {
		StringBuilder sb = new StringBuilder();
		for (Package p : pkgs) {
			sb.append(' ');
			sb.append(p.name);
			sb.append(',');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		loadSetup(args[0]);
		ConstructionPath cpBase = new ConstructionPath();
		cpBase.common.addAll(base);
		System.out.println("Using base package list of: " + csvPkgNameList(base));
		System.out.println("Size of base: " + cpBase.calculateStorageCosts()[0] / 1024 / 1024 + " MiB");
		int i = 0;
		for (ArrayList<Package> toBuild : topcontents) {
			System.out.print("Build id: " + i++);
			ConstructionPath builtSystem = new ConstructionPath();
			builtSystem.common.addAll(base);
			for (Package topPackage : toBuild) {
				System.out.print(" " + topPackage.name);
				builtSystem = ConstructionPath.merge(builtSystem, topPackage.getAllRequiredPackages());
			}
			howToBuildTopContents.add(builtSystem);
			Set<Package> path = builtSystem.getMinSizePath();
			long totSize = 0;
			for (Package p : path) {
				totSize += p.installedSize;
			}
			minPathsToTopContents.add(path);
			System.out.println(" total size: " + (totSize / 1024 / 1024) + " MiB");
		}

		System.out.println("============ Base Additions ============");
		HashSet<Package> alwaysNeeded = new HashSet<>();
		HashMap<Set<String>, Set<Package>> mixPreps = new HashMap<>();
		i = 0;
		for (Set<Package> a : minPathsToTopContents) {
			alwaysNeeded.addAll(a);
			mixPreps.put(Sets.newHashSet("" + i++), Sets.newTreeSet(a));
		}
		alwaysNeeded.removeAll(base);
		for (Set<Package> a : minPathsToTopContents) {
			alwaysNeeded.retainAll(a);
		}
		System.out.println("Packages that are additionally considered base (they are present in all builds): "
				+ csvPkgNameList(alwaysNeeded));
		for (Set<String> id : mixPreps.keySet()) {
			Set<Package> p = mixPreps.get(id);
			p.removeAll(base);
			p.removeAll(alwaysNeeded);
			ArrayList<Package> pSorted = new ArrayList<>(p);
			Collections.sort(pSorted);
			System.out.println("Additional (over base+considered base) packages required for build "
					+ id.iterator().next() + ": " + csvPkgNameList(pSorted));
		}
		System.out.println("============ Combinations ============");
		HashMap<Set<String>, Set<Package>> allCombinations = new HashMap<>(mixPreps);
		HashMap<Set<String>, Set<Package>> combinations = mixPreps;
		cleanUpReoccuringCombinations(allCombinations);
		i = 0;
		while (!(combinations = combineAllIn(combinations)).isEmpty()) {
			System.out.println(combinations.size() + " combinations returned.");
			allCombinations.putAll(combinations);
			System.out.println("============ End of iteration " + i++ + "============");
		}
		System.out.print("Cleanup... combs before: " + allCombinations.size());
		cleanUpReoccuringCombinations(allCombinations);
		System.out.println(" after: " + allCombinations.size());
		HashSet<String>[] allKeys = allCombinations.keySet().toArray(new HashSet[0]);
		HashMap<Pair<Integer, Integer>, Double> keepers = new HashMap<>();
		for (i = 0; i < allKeys.length; i++) {
			System.out.println(START_OF_FRAGMENT);
			System.out.println(CURRENT_FRAGMENT_ID + " " + i);
			System.out.print(PARENT_FRAGMENT_IDS + " ");
			boolean putComma = false;
			for (int j = 0; j < allKeys.length; j++) {
				Set<Package> iPkgs = allCombinations.get(allKeys[i]);
				Set<Package> jPkgs = allCombinations.get(allKeys[j]);
				if (j != i && iPkgs.containsAll(jPkgs)) {
					if (putComma) {
						System.out.print(",");
					}
					SimplePath spi = new SimplePath();
					spi.addAll(iPkgs);
					SimplePath spj = new SimplePath();
					spj.addAll(jPkgs);
					if (spi.getSize() > keeperThreshold && spj.getSize() > keeperThreshold) {
						SimplePath spd = new SimplePath();
						spd.addAll(Sets.symmetricDifference(iPkgs, jPkgs));
						if (spd.getSize() > keeperThreshold) {
							keepers.put(Pair.of(i, j), spd.getSize() / 1024.0 / 1024);
						}
					}
					System.out.print(j);
					putComma = true;
				}
			}
			System.out.println();
			System.out.print(CHILDREN_COMB_IDS + " ");
			putComma = false;
			for (int j = 0; j < allKeys.length; j++) {
				if (j != i && allCombinations.get(allKeys[j]).containsAll(allCombinations.get(allKeys[i]))) {
					if (putComma) {
						System.out.print(",");
					}
					System.out.print(j);
					putComma = true;
				}
			}
			System.out.println();
			ArrayList<String> cmbList = new ArrayList<>(allKeys[i]);
			Collections.sort(cmbList);
			StringBuilder sbBiDs = new StringBuilder();
			int lSize = cmbList.size();
			int lSizeDec = lSize - 1;
			for (int j = 0; j < lSize; j++) {
				sbBiDs.append(cmbList.get(j));
				if (j < lSizeDec) {
					sbBiDs.append(',');
				}
			}
			long size = 0;
			ArrayList<Package> pListInComb = new ArrayList<>(allCombinations.get(allKeys[i]));
			Collections.sort(pListInComb);
			lSize = pListInComb.size();
			for (int j = 0; j < lSize; j++) {
				size += pListInComb.get(j).installedSize;
			}
			String sizeUnit = "B";
			if (size > 10 * 1024) {
				size /= 1024;
				sizeUnit = "kiB";
			}
			if (size > 10 * 1024) {
				size /= 1024;
				sizeUnit = "MiB";
			}
			System.out.println("Combines the following build ids: " + sbBiDs);
			System.out.println(TOTAL_SIZE + " " + size + sizeUnit);
			System.out.println(PACKAGES_IN_COMBINATION + " " + csvPkgNameList(pListInComb));
			System.out.println(END_OF_FRAGMENT);
		}
		System.out.println("List of keepers:");
		for (Pair<Integer, Integer> pair : keepers.keySet()) {
			System.out.println(pair + " size: " + keepers.get(pair));
		}
	}
}
