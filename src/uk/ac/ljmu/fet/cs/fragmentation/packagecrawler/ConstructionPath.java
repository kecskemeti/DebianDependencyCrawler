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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


// Represents all possible ways a package can be built according to the pacakge
//    dependencies and alternatives found in an apt package cache.
public class ConstructionPath {
	public static class SimplePath {
		private HashSet<Package> completeSet = new HashSet<>();
		public Set<Package> completePath = Collections.unmodifiableSet(completeSet);
		private long size = 0;

		public void addAll(Set<Package> pkgs) {
			completeSet.addAll(pkgs);
			for (Package p : pkgs) {
				size += p.installedSize;
			}
		}

		public long getSize() {
			return size;
		}
	}

	public Set<Package> common = new HashSet<>();
	public long commonSize;
	public List<ConstructionPath> alternatives = new ArrayList<>();

	@Override
	public String toString() {
		StringBuilder sbC = new StringBuilder();
		for (Package p : common) {
			sbC.append(p.toString());
		}
		StringBuilder sbA = new StringBuilder();
		for (ConstructionPath cp : alternatives) {
			sbA.append(cp.toString());
		}
		return "<cp><common>" + sbC.toString() + "</common><alternatives>" + sbA.toString() + "</alternatives></cp>";
	}

	public void commonize() {
		boolean didpropagate;
		outer: do {
			didpropagate = false;
			int altSize = alternatives.size();
			if (altSize < 2) {
				return;
			} else {
				HashSet<Package> mesh = new HashSet<>(alternatives.get(0).common);
				for (int i = 1; i < altSize; i++) {
					mesh.retainAll(alternatives.get(i).common);
				}
				if (mesh.size() > 0) {
					common.addAll(mesh);
					for (int i = 0; i < altSize; i++) {
						ConstructionPath alt = alternatives.get(i);
						alt.common.removeAll(mesh);
						int preSize = alt.common.size();
						alt.commonize();
						if (alt.common.size() + alt.alternatives.size() == 0) {
							// One alternative is fully in the common part,
							// there is no need for the other alternatives to be
							// built
							alternatives.clear();
							break outer;
						}
						if (alt.common.size() != preSize) {
							didpropagate = true;
						}
					}
				}
			}
		} while (didpropagate);
		// Only leaves one empty alternative branch
		Iterator<ConstructionPath> altIt = alternatives.iterator();
		boolean canDropEmpy = false;
		while (altIt.hasNext()) {
			ConstructionPath cp = altIt.next();
			if (cp.common.isEmpty() && cp.alternatives.isEmpty()) {
				if (canDropEmpy) {
					altIt.remove();
				}
				canDropEmpy = true;
			}
		}
		// Eliminates duplicate branches
		String[] altStrings = new String[alternatives.size()];
		for (int i = 0; i < altStrings.length; i++) {
			altStrings[i] = alternatives.get(i).toString();
		}
		ArrayList<Integer> toRemove = new ArrayList<>();
		for (int i = 0; i < altStrings.length; i++) {
			for (int j = i + 1; j < altStrings.length; j++) {
				if (altStrings[i].equals(altStrings[j])) {
					if (!toRemove.contains(j)) {
						toRemove.add(j);
					}
				}
			}
		}
		Collections.sort(toRemove);
		for (int i = toRemove.size() - 1; i >= 0; i--) {
			alternatives.remove((int) toRemove.get(i));
		}
	}

	public List<SimplePath> buildSimplePaths() {
		if (alternatives.isEmpty()) {
			SimplePath sp = new SimplePath();
			sp.addAll(common);
			return Collections.singletonList(sp);
		} else {
			ArrayList<SimplePath> spRets = new ArrayList<>();
			for (ConstructionPath cp : alternatives) {
				List<SimplePath> singlePaths = cp.buildSimplePaths();
				for (SimplePath sp : singlePaths) {
					sp.addAll(common);
					spRets.add(sp);
				}
			}
			return spRets;
		}
	}

	private void updateCommonSizes() {
		commonSize = 0;
		for (Package p : common) {
			commonSize += p.installedSize;
		}
		for (ConstructionPath cp : alternatives) {
			cp.updateCommonSizes();
		}
	}

	public long[] calculateStorageCosts() {
		updateCommonSizes();
		if (alternatives.isEmpty()) {
			return new long[] { commonSize };
		} else {
			ArrayList<Long> scRets = new ArrayList<>();
			for (ConstructionPath cp : alternatives) {
				long[] singleCostRet = cp.calculateStorageCosts();
				for (long altCost : singleCostRet) {
					scRets.add(altCost + commonSize);
				}
			}
			long[] returner = new long[scRets.size()];
			for (int i = 0; i < returner.length; i++) {
				returner[i] = scRets.get(i);
			}
			return returner;
		}
	}

	public Set<Package> getMinSizePath() {
		List<SimplePath> spList = buildSimplePaths();
		SimplePath msp = Collections.min(spList, new Comparator<SimplePath>() {
			@Override
			public int compare(SimplePath o1, SimplePath o2) {
				return Long.compare(o1.getSize(), o2.getSize());
			}
		});
		return msp.completePath;
	}

	public HashSet<Package> getASinglePath() {
		HashSet<Package> returner = new HashSet<>();
		returner.addAll(common);
		if (!alternatives.isEmpty()) {
			returner.addAll(alternatives.get(0).getASinglePath());
		}
		return returner;
	}

	private static void eliminateFromAlt(ConstructionPath cp, Set<Package> toEliminate) {
		Iterator<ConstructionPath> altIt = cp.alternatives.iterator();
		while (altIt.hasNext()) {
			ConstructionPath alt = altIt.next();
			if (alt.common.size() == 0 && alt.alternatives.size() == 0) {
				continue;
			}
			alt.common.removeAll(toEliminate);
			eliminateFromAlt(alt, toEliminate);
			if (alt.common.size() == 0 && alt.alternatives.size() == 0) {
				altIt.remove();
			}
		}
	}

	public static ConstructionPath merge(ConstructionPath first, ConstructionPath second) {
		ConstructionPath merged = new ConstructionPath();
		merged.common.addAll(first.common);
		merged.common.addAll(second.common);
		merged.alternatives.addAll(first.alternatives);
		merged.alternatives.addAll(second.alternatives);
		eliminateFromAlt(merged, merged.common);
		merged.commonize();
		return merged;
	}
}