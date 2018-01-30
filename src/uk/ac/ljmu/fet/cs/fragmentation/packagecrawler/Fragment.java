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
import java.util.HashMap;
import java.util.HashSet;

// Represents an arbitrary set of packages from the package cache
public class Fragment {
	public static final HashMap<String, Fragment> allfragments = new HashMap<>();
	public static final ArrayList<Fragment> childLess = new ArrayList<>();
	public final String id;
	public final String size;
	public final String[] parentsStrings;
	public final String[] childrenStrings;
	public final HashSet<String> packageStrings = new HashSet<>();
	public final ArrayList<Fragment> parents = new ArrayList<>();
	public final ArrayList<Fragment> children = new ArrayList<>();

	public Fragment(String[] fragDef) {
		id = Package.lookForId(fragDef, EvaluateComplexRepoSitu.CURRENT_FRAGMENT_ID);
		size = Package.lookForId(fragDef, EvaluateComplexRepoSitu.TOTAL_SIZE);
		String rawPars = Package.lookForId(fragDef, EvaluateComplexRepoSitu.PARENT_FRAGMENT_IDS);
		parentsStrings = rawPars.isEmpty() ? new String[0] : rawPars.split(",");
		String rawChild = Package.lookForId(fragDef, EvaluateComplexRepoSitu.CHILDREN_COMB_IDS);
		childrenStrings = rawChild.isEmpty() ? new String[0] : rawChild.split(",");
		if (childrenStrings.length == 0) {
			childLess.add(this);
		}
		String rawPkgs = Package.lookForId(fragDef, EvaluateComplexRepoSitu.PACKAGES_IN_COMBINATION);
		String[] splitPkgs = rawPkgs.split(",");
		for (String pkg : splitPkgs) {
			packageStrings.add(pkg);
		}
		allfragments.put(id, this);
	}

	public void updateParentsArray() {
		for (String pS : parentsStrings) {
			parents.add(allfragments.get(pS));
		}
		for (String cS : childrenStrings) {
			children.add(allfragments.get(cS));
		}
	}

	public HashSet<Fragment> getAllRequired() {
		HashSet<Fragment> allReq = new HashSet<>(parents);
		for (Fragment f : parents) {
			allReq.addAll(f.getAllRequired());
		}
		return allReq;
	}

	public void clearParents() {
		if (parents.size() > 0) {
			Fragment[] origParents = parents.toArray(new Fragment[parents.size()]);
			for (Fragment f : origParents) {
				parents.removeAll(f.getAllRequired());
			}
		}
	}

	public void clearChildren() {
		if (children.size() > 0) {
			Fragment[] origChildren = children.toArray(new Fragment[children.size()]);
			children.clear();
			for (Fragment f : origChildren) {
				if (f.parents.contains(this)) {
					children.add(f);
				}
			}
		}
	}

	public static interface FragmentFactory {
		public Fragment getNewFragment(String[] fDetails);
	}

	public static void loadFromFile(String fileName, FragmentFactory factory) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		ArrayList<String> lineCache = new ArrayList<>();
		String line;
		boolean doCacheing = false;
		while ((line = br.readLine()) != null) {
			if (doCacheing) {
				if (line.equals(EvaluateComplexRepoSitu.END_OF_FRAGMENT)) {
					factory.getNewFragment(lineCache.toArray(new String[lineCache.size()]));
					lineCache.clear();
					doCacheing = false;
				} else {
					lineCache.add(line);
				}
			} else {
				doCacheing = line.equals(EvaluateComplexRepoSitu.START_OF_FRAGMENT);
			}
		}
		br.close();
		Collection<Fragment> allFrList = Fragment.allfragments.values();
		for (Fragment f : allFrList) {
			f.updateParentsArray();
		}
		for (Fragment f : allFrList) {
			f.clearParents();
		}
		for (Fragment f : allFrList) {
			f.clearChildren();
		}
		for (Fragment f : allFrList) {
			HashSet<Fragment> allparents = f.getAllRequired();
			for (Fragment p : allparents) {
				f.packageStrings.removeAll(p.packageStrings);
			}
		}
	}
}
