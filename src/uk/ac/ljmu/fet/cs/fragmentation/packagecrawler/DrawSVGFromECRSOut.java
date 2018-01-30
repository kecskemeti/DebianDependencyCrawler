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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

// Draws and SVG from the output of the EvaluateComplexRepoSituation
public class DrawSVGFromECRSOut {
	public static double maxXwidth = -1;
	public static final double fragCircleRadius = 0.75;
	public static final double xSkew = 0.08;
	public static final String fragColour = "black";
	public static final String buildColour = "red";
	public static final double textXShift = fragCircleRadius * 2;
	public static final double textYShift = fragCircleRadius * 1.7;
	public static final double fontSize = fragCircleRadius * 5;
	public static final double minfragGap = fragCircleRadius * 45;
	public static final double layerGap = fragCircleRadius * 20;
	public static final double linewidth = fragCircleRadius / 3.5;
	public static final double arrowHeadLength = fragCircleRadius * 1.8;
	public static final double arrowHeadHalfHeight = arrowHeadLength / 1.2 / 2;
	public static final double margin = fragCircleRadius * 4;
	public static int arrR = 255;
	public static int arrG = 0;
	public static int arrB = 255;
	public static int blueStep = 1;

	public static String genRGBString(int currR, int currG, int currB) {
		StringBuffer sb = new StringBuffer();
		sb.append("\"rgb(");
		sb.append(currR);
		sb.append(",");
		sb.append(currG);
		sb.append(",");
		sb.append(currB);
		sb.append(")\"");
		return sb.toString();
	}

	public static class SVGFragment extends Fragment {
		public static final HashMap<Double, ArrayList<SVGFragment>> levelledFragments = new HashMap<>();
		public static final HashMap<Double, Double> levelCurrPos = new HashMap<>();
		public static final HashMap<Double, Double> levelStep = new HashMap<>();
		double x = -1, y = margin - layerGap;

		public SVGFragment(String[] details) {
			super(details);
		}

		public void calcLevels() {
			if (y > 0)
				return;
			double maxLev = y;
			for (Fragment pOrig : parents) {
				SVGFragment p = (SVGFragment) pOrig;
				if (p.y < 0) {
					p.calcLevels();
				}
				maxLev = Math.max(p.y, maxLev);
			}
			y = maxLev + layerGap;
			ArrayList<SVGFragment> levelPeers = levelledFragments.get(y);
			if (levelPeers == null) {
				levelPeers = new ArrayList<>();
				levelledFragments.put(y, levelPeers);
			}
			levelPeers.add(this);
		}

		private int getPlacedParentCount() {
			int pc = 0;
			for (Fragment pOrig : parents) {
				SVGFragment p = (SVGFragment) pOrig;
				pc += p.x > 0 ? 1 : 0;
			}
			return pc;
		}

		private boolean placeChildren() {
			final ArrayList<Fragment> notYet = new ArrayList<>();
			do {
				notYet.clear();
				final ArrayList<Integer> npCounts = new ArrayList<>();
				int minNPCount = Integer.MAX_VALUE;
				for (Fragment fOrig : children) {
					SVGFragment f = (SVGFragment) fOrig;
					if (!f.placeOnGrid(true)) {
						notYet.add(f);
						final int currNPCount = f.getPlacedParentCount();
						npCounts.add(currNPCount);
						minNPCount = Math.min(minNPCount, currNPCount);
					}
				}
				if (notYet.size() > 0) {
					Fragment needsAllParents = notYet.get(npCounts.indexOf(minNPCount));
					int okwithparents = 0;
					for (Fragment pOrig : needsAllParents.parents) {
						SVGFragment p = (SVGFragment) pOrig;
						if (p.getPlacedParentCount() == p.parents.size()) {
							okwithparents++;
						}
					}
					if (okwithparents == needsAllParents.parents.size()) {
						for (Fragment pOrig : needsAllParents.parents) {
							SVGFragment p = (SVGFragment) pOrig;
							if (p.x < 0) {
								p.placeOnGrid(false);
							}
						}
					} else {
						return false;
					}
				}
			} while (notYet.size() > 0);
			return true;
		}

		public boolean placeOnGrid(boolean withChildren) {
			if (x > 0) {
				if (withChildren) {
					placeChildren();
					return true;
				}
				throw new RuntimeException("...");
			}
			if (getPlacedParentCount() == parents.size()) {
				x = levelCurrPos.get(y);
				levelCurrPos.put(y, x + levelStep.get(y));
				x += levelStep.get(y) * (xSkew * Math.random() - xSkew);
				if (withChildren) {
					if (placeChildren()) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		}

		public int calcPossiblePlaces() {
			int placeSum = 0;
			if (getPlacedParentCount() == parents.size()) {
				placeSum++;
				x = levelCurrPos.get(y);
				levelCurrPos.put(y, x + levelStep.get(y));
				for (Fragment cOrig : children) {
					SVGFragment c = (SVGFragment) cOrig;
					placeSum += c.calcPossiblePlaces();
				}
				levelCurrPos.put(y, x);
				x = -1;
			}
			return placeSum;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("<g><g>");
			// The arrows towards the children
			for (Fragment fOrig : children) {
				SVGFragment f = (SVGFragment) fOrig;
				double xdiff = x - f.x;
				double ydiff = y - f.y;
				double d = Math.sqrt(Math.pow(xdiff, 2) + Math.pow(ydiff, 2));
				double scaleToRadius = fragCircleRadius / d;
				double endX = f.x + xdiff * scaleToRadius;
				double endY = f.y + ydiff * scaleToRadius;
				int currR = arrR;
				int currB = arrB;
				int currG = arrG;
				arrB -= blueStep;
				if (arrB < 0) {
					arrB = 255;
					if (currR == 0) {
						arrG--;
					} else {
						arrR--;
					}
					if (arrG < 0) {
						arrG = 255;
					}
					if (arrR < 0) {
						arrR = 255;
					}
				}
				// Arrow line
				sb.append("<g fill=");
				String genColor = genRGBString(currR, currG, currB);
				sb.append(genColor);
				sb.append(" stroke=");
				sb.append(genColor);
				sb.append("><line x1=\"");
				sb.append(x - xdiff * scaleToRadius);
				sb.append("\" y1=\"");
				sb.append(y - ydiff * scaleToRadius);
				sb.append("\" x2=\"");
				sb.append(endX);
				sb.append("\" y2=\"");
				sb.append(endY);
				sb.append("\" stroke-width=\"");
				sb.append(linewidth);
				sb.append("\"/>");
				// Arrow head
				sb.append("<use transform=\"translate(");
				sb.append(endX);
				sb.append(",");
				sb.append(endY);
				sb.append(") rotate(");
				sb.append(90 - 180 * Math.atan(xdiff / ydiff) / Math.PI);
				sb.append(")\" xlink:href=\"#arrowhead\" /></g>");
			}
			// Switching colour for next arrow set
			// The circle for the fragment
			int temp = arrR;
			arrR = arrG;
			arrG = temp;
			// Drawing the actual circle for the fragment
			sb.append("</g><circle cx=\"");
			sb.append(x);
			sb.append("\" cy=\"");
			sb.append(y);
			sb.append("\" r=\"");
			sb.append((children.isEmpty() ? 1.3 : 1) * fragCircleRadius);
			sb.append("\" fill=\"");
			sb.append(children.isEmpty() ? buildColour : fragColour);
			sb.append("\"/>");
			sb.append("</g>");
			return sb.toString();
		}

		public String getTextSVG() {
			StringBuilder sb = new StringBuilder();
			sb.append("<text x=\"");
			sb.append(x + textXShift);
			sb.append("\" y=\"");
			sb.append(y + textYShift);
			sb.append("\" fill=\"");
			sb.append(fragColour);
			sb.append("\" font-size=\"");
			sb.append(fontSize);
			sb.append("\">");
			sb.append(id);
			sb.append(" (");
			sb.append(size);
			sb.append(")</text>");
			return sb.toString();
		}
	}

	public static void main(String[] args) throws Exception {
		Fragment.loadFromFile(args[0], new Fragment.FragmentFactory() {
			@Override
			public Fragment getNewFragment(String[] fDetails) {
				return new SVGFragment(fDetails);
			}
		});
		ArrayList<SVGFragment> allFrList = new ArrayList<>();
		for (Fragment f : Fragment.allfragments.values()) {
			allFrList.add((SVGFragment) f);
		}

		for (SVGFragment f : allFrList) {
			f.calcLevels();
		}
		for (Double d : SVGFragment.levelledFragments.keySet()) {
			maxXwidth = Math.max(SVGFragment.levelledFragments.get(d).size() * minfragGap, maxXwidth);
		}
		for (Double d : SVGFragment.levelledFragments.keySet()) {
			SVGFragment.levelStep.put(d, minfragGap);
			SVGFragment.levelCurrPos.put(d,
					(maxXwidth - 2 * margin - minfragGap * (SVGFragment.levelledFragments.get(d).size() - 1)) / 2);
		}
		double pixelHeight = SVGFragment.levelledFragments.keySet().stream().max(new Comparator<Double>() {
			@Override
			public int compare(Double o1, Double o2) {
				return Double.compare(o1, o2);
			}
		}).get().doubleValue() + margin;
		double pixelWidth = maxXwidth + margin * 2;
		System.out.println(
				"<?xml version=\"1.0\" standalone=\"no\"?>\n" + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\"\n"
						+ "  \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n" + "<svg width=\""
						+ (pixelWidth / 100) + "cm\" height=\"" + (pixelHeight / 100) + "cm\" viewBox=\"0 0 "
						+ pixelWidth + " " + pixelHeight + "\"\n"
						+ "     xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\">\n"
						+ "<defs>\n" + "<g id=\"arrowhead\">\n" + "<line x1=\"0\" y1=\"0\" x2=\"" + (-arrowHeadLength)
						+ "\" y2=\"" + arrowHeadHalfHeight + "\" stroke-width=\"" + linewidth + "\" />\n"
						+ "<line x1=\"0\" y1=\"0\" x2=\"" + (-arrowHeadLength) + "\" y2=\"" + (-arrowHeadHalfHeight)
						+ "\" stroke-width=\"" + linewidth + "\" />\n" + "<circle cx=\"0\" cy=\"0\" r=\""
						+ linewidth / 2 + "\" stroke-width=\"0\"/>\n" + "</g>\n" + "</defs>");
		if (!args[1].equals("alt")) {
			boolean anotherLoop;
			do {
				anotherLoop = false;
				int maxPlaces = -1;
				SVGFragment toUse = null;
				for (SVGFragment f : allFrList) {
					boolean stillNeedsPlacement = f.x < 0;
					anotherLoop |= stillNeedsPlacement;
					if (stillNeedsPlacement) {
						int myPlaces = f.calcPossiblePlaces();
						if (myPlaces > maxPlaces) {
							toUse = f;
							maxPlaces = myPlaces;
						}
					}
				}
				if (toUse != null) {
					toUse.placeOnGrid(false);
				}
			} while (anotherLoop);
		} else {
			boolean anotherLoop;
			do {
				anotherLoop = true;
				for (SVGFragment f : allFrList) {
					anotherLoop &= f.placeOnGrid(true);
				}
			} while (anotherLoop);
		}
		blueStep = (int) Math.max(1, Math.log(10000 / allFrList.size() / Math.log(1.1)));
		System.err.println("using bluestep: " + blueStep);

		Double[] levelKeys = SVGFragment.levelledFragments.keySet().toArray(new Double[0]);
		Arrays.sort(levelKeys);
		// Print out all layers starting from the bottom (allowing the overlaps
		// from
		// arrows)
		for (int i = levelKeys.length - 1; i >= 0; i--) {
			ArrayList<SVGFragment> inLevelFragments = SVGFragment.levelledFragments.get(levelKeys[i]);
			inLevelFragments.sort(new Comparator<SVGFragment>() {
				@Override
				public int compare(SVGFragment o1, SVGFragment o2) {
					return Double.compare(o1.x, o2.x);
				}
			});
			// We print out every level in its X order so we will have
			// alternating colours
			// in each row
			for (Fragment f : inLevelFragments) {
				System.out.println(f);
			}
		}
		for (SVGFragment f : allFrList) {
			System.out.println(f.getTextSVG());
		}
		System.out.println("</svg>");
	}
}
