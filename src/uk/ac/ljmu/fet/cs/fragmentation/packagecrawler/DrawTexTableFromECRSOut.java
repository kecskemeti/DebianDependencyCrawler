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
import java.util.Comparator;

// Allows presenting the results of the EvaluateComplexRepoSituation's main output in a latex table
public class DrawTexTableFromECRSOut {

	public static class TeXFragment extends Fragment {
		private static final String TABLESEP = " & ";

		public TeXFragment(String[] details) {
			super(details);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(id);
			sb.append(TABLESEP);
			sb.append(size);
			sb.append(TABLESEP);
			for (Fragment f : parents) {
				sb.append(f.id);
				sb.append(",");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append(TABLESEP);
			if (packageStrings.size() > 0) {
				for (String pkg : packageStrings) {
					sb.append(pkg);
					sb.append(",");
				}
				sb.deleteCharAt(sb.length() - 1);
			}
			sb.append("\\\\");
			return sb.toString();
		}
	}

	public static void main(String[] args) throws Exception {
		Fragment.loadFromFile(args[0], new Fragment.FragmentFactory() {
			@Override
			public Fragment getNewFragment(String[] fDetails) {
				return new TeXFragment(fDetails);
			}
		});
		ArrayList<Fragment> allFrList = new ArrayList<>(Fragment.allfragments.values());
		allFrList.sort(new Comparator<Fragment>() {
			@Override
			public int compare(Fragment o1, Fragment o2) {
				return Integer.compare(Integer.parseInt(o1.id), Integer.parseInt(o2.id));
			}
		});
		System.out.println(
				"\\begin{longtable}{lccp{9cm}\n\\hline\nId & Size & Combines & Extends with packages\\\\\n\\hline");
		for (Fragment f : allFrList) {
			System.out.println(f.toString());
		}
		System.out.println(
				"\\hline\n\\caption{List of identified fragments and their properties in the original 16 different VMIs}\n\\end{longtable}");
	}
}
