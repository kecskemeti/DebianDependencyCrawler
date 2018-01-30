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
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// Internal representation of debian packages + package caches
public class Package implements Comparable<Package> {
	public static enum UpdateLevel implements Comparable<UpdateLevel> {
		main, backports, security, updates;
		public static UpdateLevel getLevel(String fileName) {
			String[] splitFN = fileName.split("_");
			int distsIdx = 0;
			for (int i = 0; i < splitFN.length; i++) {
				if (splitFN[i].equals("dists")) {
					distsIdx = i;
					break;
				}
			}
			String levelId = splitFN[fileName.contains("debian") ? distsIdx - 1 : distsIdx + 1];
			String[] l = levelId.split("-");
			if (l.length == 1) {
				return main;
			} else {
				return valueOf(l[1]);
			}
		}
	}

	public final static HashMap<String, Package> completeList = new HashMap<>();
	public final static HashSet<Package> essentials = new HashSet<>();

	public final static String nameID = "Package:";
	public final static String depID = "Depends:";
	public final static String prDepID = "Pre-Depends:";
	public final static String essID = "Essential:";
	public final static String csizeID = "Size:";
	public final static String isizeID = "Installed-Size:";
	public final static String provID = "Provides:";
	public final static String repID = "Replaces:";
	public final static String[] bannedNamePrefixes = { "lib", "linux", "language", "texlive-doc-" };
	public final static String[] bannedNamePostfixes = { "-dbg", "-dev", "-docs", "-doc" };

	public final long compressedSize;
	public final long installedSize;
	public final boolean predictedInstallSize;
	public final String name;
	public final UpdateLevel level;

	public final ArrayList<ArrayList<Package>> parents = new ArrayList<>();
	public final ArrayList<Package> children = new ArrayList<>();

	private ConstructionPath constructionPaths = null;

	// Only available before parents and children arrays are populated
	private ArrayList<ArrayList<String>> parentNames = new ArrayList<>();
	private ArrayList<ArrayList<String>> altNames = new ArrayList<>();

	private Package(String[] pkgDef, UpdateLevel ul) {
		name = lookForId(pkgDef, nameID);
		level = ul;
		compressedSize = Long.parseLong(lookForId(pkgDef, csizeID));
		String iSizeStr = lookForId(pkgDef, isizeID);
		if (iSizeStr == null) {
			// Median multiplier coming from the compressed and installed size
			// ratios
			installedSize = (long) (compressedSize * 2.7775);
			predictedInstallSize = true;
		} else {
			// Median multiplier coming from the reported and real installed
			// sizes
			installedSize = (long) (Long.parseLong(iSizeStr) * 0.992 * 1024);
			predictedInstallSize = false;
		}
		parseDepList(lookForId(pkgDef, depID), parentNames);
		parseDepList(lookForId(pkgDef, prDepID), parentNames);
		parseDepList(lookForId(pkgDef, provID), altNames);
		parseDepList(lookForId(pkgDef, repID), altNames);
		if (lookForId(pkgDef, essID) != null) {
			essentials.add(this);
		}
		Package prev;
		if ((prev = completeList.get(name)) != null) {
			int compResult = prev.level.compareTo(level);
			if (compResult > 0) {
				// no need to store, there is a better package already
				return;
			} else if (compResult == 0) {
				// System.err.println(
				// "WARNING same name (" + name + ") same level (" + level + ")
				// package, keeping
				// the later one!");
			}
		}
		completeList.put(name, this);
	}

	private void parseDepList(String deplist, ArrayList<ArrayList<String>> toStore) {
		if (deplist != null) {
			String[] rawDepList = deplist.split(",");
			for (String dep : rawDepList) {
				String[] alternatives = dep.split("\\|");
				ArrayList<String> altList = new ArrayList<>();
				outsideloop: for (String alternative : alternatives) {
					String actualAltName = alternative.trim().split(" ")[0];
					for (ArrayList<String> prevParents : toStore) {
						if (prevParents.contains(actualAltName)) {
							continue outsideloop;
						}
					}
					altList.add(actualAltName);
				}
				if (!altList.isEmpty()) {
					toStore.add(altList);
				}
			}
		}
	}

	public static String lookForId(String[] pkgDef, String prefixToLookFor) {
		for (String defLine : pkgDef) {
			if (defLine.startsWith(prefixToLookFor)) {
				return prefixToLookFor.length() > defLine.length() ? ""
						: defLine.substring(prefixToLookFor.length() + 1);
			}
		}
		return null;
	}

	private void generatePackageArrays() {
		for (ArrayList<String> altList : parentNames) {
			ArrayList<Package> altPList = new ArrayList<>();
			for (String alt : altList) {
				Package altPkg = completeList.get(alt);
				// Reference to nonexistent package
				if (altPkg != null) {
					altPList.add(altPkg);
					altPkg.children.add(this);
				}
			}
			parents.add(altPList);
		}
		parentNames = null;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Package) {
			return ((Package) obj).name.equals(name);
		} else {
			return false;
		}
	}

	boolean notInCurrentTrace = true;

	public ConstructionPath getAllRequiredPackages() {
		if (notInCurrentTrace) {
			if (constructionPaths != null) {
				return constructionPaths;
			}
			notInCurrentTrace = false;
			ConstructionPath returner = new ConstructionPath();
			returner.common.add(this);
			for (ArrayList<Package> altParentSubList : parents) {
				if (altParentSubList.size() == 1) {
					ConstructionPath lowLevelReturner = altParentSubList.get(0).getAllRequiredPackages();
					if (lowLevelReturner != null) {
						returner = ConstructionPath.merge(returner, lowLevelReturner);
					}
				} else {
					for (Package altPackage : altParentSubList) {
						ConstructionPath lowLevelReturner = altPackage.getAllRequiredPackages();
						if (lowLevelReturner != null) {
							returner.alternatives.add(lowLevelReturner);
						}
					}
				}
			}
			returner.commonize();
			notInCurrentTrace = true;
			constructionPaths = returner;
			return returner;
		} else {
			return null;
		}
	}

	public static void crawlPackageFolder(String folder) throws IOException {
		File dir = new File(folder);
		File[] flist = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith("_Packages");
			}
		});
		for (File pdefs : flist) {
			UpdateLevel ul = UpdateLevel.getLevel(pdefs.getName());
			BufferedReader br = new BufferedReader(new FileReader(pdefs));
			ArrayList<String> lineCache = new ArrayList<>();
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty()) {
					new Package(lineCache.toArray(new String[lineCache.size()]), ul);
					lineCache.clear();
				} else {
					lineCache.add(line);
				}
			}
			br.close();
		}
		for (Package p : completeList.values()) {
			p.generatePackageArrays();
		}
	}

	public static ArrayList<Package> loadDPKGList(String dpkgListFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(dpkgListFile));
		String line;
		ArrayList<Package> returner = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			if (line.startsWith("ii")) {
				Package p = completeList.get(line.split("\\s+")[1]);
				if (p != null) {
					returner.add(p);
				}
			}
		}
		br.close();
		return returner;
	}

	public static void clearUselessChildLess() {
		boolean didRemovals;
		do {
			didRemovals = false;
			Package[] rawSet = completeList.values().toArray(new Package[0]);
			for (Package p : rawSet) {
				if (p.children.isEmpty()) {
					boolean inIt = false;
					for (String prefix : bannedNamePrefixes) {
						inIt |= p.name.startsWith(prefix);
					}
					for (String postfix : bannedNamePostfixes) {
						inIt |= p.name.endsWith(postfix);
					}
					if (inIt) {
						didRemovals = true;
						completeList.remove(p.name);
						for (ArrayList<Package> altParents : p.parents) {
							for (Package singleParent : altParents) {
								singleParent.children.remove(p);
							}
						}
					}
				}
			}
		} while (didRemovals);
	}

	@Override
	public String toString() {
		return "<package name=\"" + name + "\" size=\"" + installedSize + "\" />";
	}

	@Override
	public int compareTo(Package o) {
		return this.name.compareTo(o.name);
	}
}
