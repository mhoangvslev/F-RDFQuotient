package fr.inria.cedar.quotientSummary.datastructures;

import java.util.ArrayList;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ReplacementSpecification {
	private static final Logger LOGGER = Logger.getLogger(ReplacementSpecification.class.getName());
	private final Long sc;
	private final Long tc;
	private final Long oldNode;
	private final Long newNode;

	public ReplacementSpecification(Long sc, Long tc, Long oldNode, Long newNode) {
		LOGGER.setLevel(Level.INFO);
		this.sc = sc;
		this.tc = tc;
		this.oldNode = oldNode;
		this.newNode = newNode;
	}

	public Long getSC() {
		return sc;
	}

	public Long getTC() {
		return tc;
	}

	public Long getOldNode() {
		return oldNode;
	}

	public Long getNewNode() {
		return newNode;
	}

	public void checkForConflicts(ArrayList<ReplacementSpecification> nodeReps) {
		for (ReplacementSpecification rs2: nodeReps){
			checkForConflict(rs2);
		}
	}

	private void checkForConflict(ReplacementSpecification rs2) {
		if (sc.equals(rs2.getSC())) {
			if (tc.equals(rs2.getTC())) {
				if (oldNode.equals(rs2.getOldNode())) {
					if (!newNode.equals(rs2.getNewNode())) {
						throw new IllegalStateException("Incompatible replacements of " + oldNode);
					}
				}
			}
		}
	}
}
