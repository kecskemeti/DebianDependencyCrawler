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
import java.util.HashSet;

// Determines the list of ancestor packages needed for a particular package (ie., what is needed to make it installed)
// Then calculates the size of all the necessary packages for the particular package in question.
//    (ie., gives an estimated size of the VM image that will hold the package)
public class Crawler {

	public static void main(String[] args) throws Exception {
		Package.crawlPackageFolder(args[0]);
		ArrayList<Package> baseDistro = new ArrayList<>();
		int argsIndex = 1;
		if (args[1].startsWith("-l")) {
			baseDistro = Package.loadDPKGList(args[1].substring(2));
			argsIndex++;
		} else {
			baseDistro.addAll(Package.essentials);
		}
		System.out.println("Base distribution used:");
		System.out.println(baseDistro);
		ConstructionPath bdCP = new ConstructionPath();
		bdCP.common.addAll(baseDistro);
		System.out.println("Size of the base distro: " + bdCP.calculateStorageCosts()[0]);
		System.out.println("==============");
		ArrayList<ConstructionPath> requiredPaths = new ArrayList<>();
		for (int i = argsIndex; i < args.length; i++) {
			System.out.println("Constructed paths for package " + args[i] + ":");
			ConstructionPath cp = Package.completeList.get(args[i]).getAllRequiredPackages();
			requiredPaths.add(cp);
			System.out.println(cp);
		}
		System.out.println("==============");
		System.out.println("Merging..");
		ConstructionPath mergedPath = new ConstructionPath();
		for (ConstructionPath cp : requiredPaths) {
			mergedPath = ConstructionPath.merge(mergedPath, cp);
		}
		System.out.println("Merging results:");
		System.out.println(mergedPath);
		System.out.println("Adding base distro:");
		mergedPath = ConstructionPath.merge(mergedPath, bdCP);
		System.out.println(mergedPath);
		System.out.println("==============");
		System.out.println("Calculating complete package size:");
		long[] sizeList = mergedPath.calculateStorageCosts();
		for (long l : sizeList) {
			System.out.println(l);
		}
		System.out.println("==============");
		System.out.println("Addition over base:");
		HashSet<Package> oneCompleteSet = mergedPath.getASinglePath();
		oneCompleteSet.removeAll(baseDistro);
		ArrayList<Package> remaining = new ArrayList<>(oneCompleteSet);
		Collections.sort(remaining);
		System.out.println(remaining);
	}
}
