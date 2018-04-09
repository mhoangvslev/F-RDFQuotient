package fr.inria.cedar.quotientSummary.strong;

import java.util.HashMap;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongList;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;

public class StrongOrTypedStrongSummary extends Summary {
	Long2LongList sc; // for each source clique ID,  a source clique
	Long2LongList tc; // for each target clique ID,  its target clique
	Long2Long n2sc; // for each node, its source clique ID
	Long2Long n2tc; // for each node, its target clique ID
	Long2Long p2sc; // property to source clique
	Long2Long p2tc; // property to target clique

	long minCliqueID;
	// for debugging
	long globalTripleCount; 

	protected long numberOfDataTriplesRead; 
	protected long numberOfTypeTriplesRead; 

	protected long emptySCCount; // le numéro de la clique vide
	protected long emptyTCCount; // le numéro de la clique vide
	
	public StrongOrTypedStrongSummary(){
		super(); 
		sc = new Long2LongList();
		tc = new Long2LongList();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		rep = new Long2Long();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE; 
		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
	}
}
