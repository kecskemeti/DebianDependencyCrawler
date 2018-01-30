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

import java.util.Set;

// Lists the packages in a package cache that does not have dependent packages.
// This is intended to help finding packages which could be useful for end users.
public class ListChildLess {
	public static void main(String[] args) throws Exception {
		System.err.print("Loading package lists: ");
		Package.crawlPackageFolder(args[0]);
		System.err.println("done.");
		System.err.println("Package count: " + Package.completeList.size());
		System.err.print("Cleanup of childless packages: ");
		Package.clearUselessChildLess();
		System.err.println("done.");
		System.err.println("Package count after cleanup: " + Package.completeList.size());
		ConstructionPath base = new ConstructionPath();
		base.common.addAll(Package.essentials);
		for (Package p : Package.completeList.values()) {
			if (p.children.isEmpty()) {
				ConstructionPath cp = p.getAllRequiredPackages();
				cp = ConstructionPath.merge(base, cp);
				Set<Package> pList = cp.getMinSizePath();
				long size = 0;
				for (Package cpP : pList) {
					size += cpP.installedSize;
				}
				if (size > 400 * 1024 * 1024 && (pList.size() - Package.essentials.size() > 20)) {
					System.out.println(p.name);
				}
			}
		}
	}
}
