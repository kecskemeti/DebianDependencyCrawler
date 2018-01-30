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

// Prints out the list of packages that are only needed for the package specified in the third CLI parameter
public class CalcListDiff {
	public static void main(String[] args) throws Exception {
		Package.crawlPackageFolder(args[0]);
		HashSet<Package> small = new HashSet<>(Package.loadDPKGList(args[1]));
		HashSet<Package> big = new HashSet<>(Package.loadDPKGList(args[2]));
		big.removeAll(small);
		ArrayList<Package> remaining = new ArrayList<>(big);
		Collections.sort(remaining);
		System.out.println(remaining);
	}
}
