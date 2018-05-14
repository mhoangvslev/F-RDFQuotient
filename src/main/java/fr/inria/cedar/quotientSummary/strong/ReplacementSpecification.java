package fr.inria.cedar.quotientSummary.strong;

import java.util.ArrayList;

public class ReplacementSpecification {

	Long sc;
	Long tc;
	Long oldNode;
	Long newNode;
	
	public ReplacementSpecification(Long sc, Long tc, Long oldNode, Long newNode){
		this.sc = sc;
		this.tc = tc; 
		this.oldNode = oldNode;
		this.newNode = newNode;
	}
	
	/*
	 * if we want to remove all over
	 */
	public ReplacementSpecification(Long oldNode, Long newNode){
		this.oldNode = oldNode;
		this.newNode = newNode;
	}

	public Long getSC(){
		return sc;
	}
	public Long getTC(){
		return tc; 
	}
	public Long getOldNode(){
		return oldNode;
	}
	public Long getNewNode(){
		return newNode; 
	}

	public void checkForConflicts(ArrayList<ReplacementSpecification> nodeReps) {
		for (ReplacementSpecification rs2: nodeReps){
			checkForConflict(rs2); 
		}
	}

	private void checkForConflict(ReplacementSpecification rs2) {
		if (this.sc.equals(rs2.getSC())){
			if (this.tc.equals(rs2.getTC())){
				if (this.oldNode.equals(rs2.getOldNode())){
					if (this.newNode.equals(rs2.getNewNode())){
						throw new IllegalStateException("Incompatible replacements of " + oldNode); 
					}
				}
			}
		}
	}
}
