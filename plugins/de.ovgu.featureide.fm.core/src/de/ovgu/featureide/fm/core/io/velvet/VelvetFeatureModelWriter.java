/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2013  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 * 
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.fm.core.io.velvet;

import de.ovgu.featureide.fm.core.Constraint;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.io.AbstractFeatureModelWriter;

/**
 * Writes the feature model to a string in velvet syntax.
 * 
 * @author Sebastian Krieter
 */
public class VelvetFeatureModelWriter extends AbstractFeatureModelWriter {

	private static final String[] SYMBOLS = { "!", "&&", "||", "->", "<->",
			", ", "choose", "atleast", "atmost" };
	private static final String NEWLINE = System.getProperty("line.separator");
	private StringBuilder sb = new StringBuilder();

	/**
	 * If true an interface will be created. Otherwise it is named "concept"
	 */
	private boolean isInterface = false;

	public VelvetFeatureModelWriter(FeatureModel featureModel) {
		setFeatureModel(featureModel);
	}

	public VelvetFeatureModelWriter(FeatureModel featureModel,
			boolean isInterface) {
		this(featureModel);
		this.isInterface = true;
	}

	@Override
	public String writeToString() {
		Feature root = featureModel.getRoot();

		if (isInterface)
			sb.append("cinterface ");
		else
			sb.append("concept ");
		sb.append(root.getName());
		sb.append(" {");
		sb.append(NEWLINE);
		
		if (root.isAnd()) {
			for (Feature feature : root.getChildren()) {
				writeFeature(feature, 1);
			}
		} else if (root.isOr()) {
			sb.append("\tsomeOf {");
			sb.append(NEWLINE);
			for (Feature feature : root.getChildren()) {
				writeFeature(feature, 2);
			}
			sb.append("\t}");
		} else if (root.isAlternative()) {
			sb.append("\toneOf {");
			sb.append(NEWLINE);
			for (Feature f : root.getChildren()) {
				writeFeature(f, 2);
			}
			sb.append("\t}");
			sb.append(NEWLINE);
		}
		
		for (Constraint constraint : featureModel.getConstraints()) {
			sb.append("\tconstraint ");
			sb.append(constraint.getNode().toString(SYMBOLS));
			sb.append(";");
			sb.append(NEWLINE);
		}
		sb.append("}");

		return sb.toString();
	}

	private void writeFeature(Feature curFeature, int depth) {
		writeTab(depth);
		if (curFeature.isAbstract()) {
			sb.append("abstract ");
		}
		if (curFeature.isMandatory() && (curFeature.getParent() == null || curFeature.getParent().isAnd())) {
			sb.append("mandatory ");
		}
		sb.append("feature ");
		sb.append(curFeature.getName());

		if (curFeature.getChildrenCount() == 0) {
			sb.append(";");
		} else {
			sb.append(" {");
			sb.append(NEWLINE);

			if (curFeature.isAnd()) {
				for (Feature feature : curFeature.getChildren()) {
					writeFeature(feature, depth + 1);
				}
			} else if (curFeature.isOr()) {
				writeTab(depth + 1);
				sb.append("someOf {");
				sb.append(NEWLINE);
				for (Feature feature : curFeature.getChildren()) {
					writeFeature(feature, depth + 2);
				}
				writeTab(depth + 1);
				sb.append("}");
				sb.append(NEWLINE);
			} else if (curFeature.isAlternative()) {
				writeTab(depth + 1);
				sb.append("oneOf {");
				sb.append(NEWLINE);
				for (Feature f : curFeature.getChildren()) {
					writeFeature(f, depth + 2);
				}
				writeTab(depth + 1);
				sb.append("}");
				sb.append(NEWLINE);
			}
			writeTab(depth);
			sb.append("}");
		}
		sb.append(NEWLINE);
	}

	private void writeTab(int times) {
		for (int i = 0; i < times; i++) {
			sb.append('\t');
		}
	}
}
