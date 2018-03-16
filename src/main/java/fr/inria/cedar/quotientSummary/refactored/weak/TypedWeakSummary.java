package fr.inria.cedar.quotientSummary.refactored.weak;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.refactored.Summary;

public class TypedWeakSummary extends WeakOrTypedWeakSummary {
	
	/**
	 * This must be used to read a summary from Postgres. It is based on the core summary population method of the root summary class,
	 * then we just steal its edges.
	 * @param conn
	 */
	public  TypedWeakSummary (Connection conn) {
		Summary s = Summary.readSummaryFromPostgres(conn);
		this.edges = s.getEdgesAsInternallyStored(); 
	}
	
	
	public TypedWeakSummary() {
		super(); 
	}


	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set	
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2Long c2cs; // class to class set
	

	/**
	 * @param typeTriplesFile
	 * @param dataTriplesFile
	 * @param method
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) {
		long start = System.currentTimeMillis(); 
		try {

			// First file: type triples
			try (BufferedReader br = new BufferedReader(new FileReader(new File(typeTriplesFile)))) {
				while (br.ready()){
					String spo = br.readLine();
					Triple t = readTriple(spo);
					//t.display();
					handleTypeTripleAfterData(t);
					//System.out.println();
				}
			}
			//System.out.println("=== After weak type triple summarization of " + typeTriplesFile + ": =================================== ");
			//display();
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not exploit file " + typeTriplesFile); 
		}
			
		//  Second file: data triples	
		try (BufferedReader br = new BufferedReader(new FileReader(new File(dataTriplesFile)))) {
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//System.out.println("\n");
				//t.display();
				handleDataTriple(t);
				//display();
				//System.out.println();
			}
		}
		//System.out.println("=== After weak data triple summarization of "+ dataTriplesFile + ": ==================================");
		//display();

		catch(IOException e) {
			throw new IllegalStateException("Unable to open file " + dataTriplesFile + " or " + typeTriplesFile + ": " + e.toString()); 
		}
		long stop = System.currentTimeMillis();
		System.out.println("Typed weak summarization took: "+ (stop - start));
		display(dataTriplesFile); // this prints out and makes a DOT file
	}
	
	public void handleTypeTripleBeforeData(Triple t){
		//display();
		// the following three lines were used to "learn" the URI for rdf:type. 
//		if (firstType){
//			TYPE = t.p;
//			firstType=false; 
//		}
		Long classSetIDForThisType = c2cs.get(t.o);
		if (classSetIDForThisType == null){
			classSetIDForThisType = new Long(maxSummaryNode);
			c2cs.put(new Long(t.o), classSetIDForThisType);
			Debugger.log("Created class set ID " + classSetIDForThisType + " for type " + t.o);
			maxSummaryNode++;
		}
		// now classSetIDForThisType exists
		// create the class set for this type if necessary:
		ArrayList<Long> thisTypeClassSet = cs.get(classSetIDForThisType);
		if (thisTypeClassSet == null){
			thisTypeClassSet = new ArrayList<>();
			cs.put(classSetIDForThisType, thisTypeClassSet);

		}
		// add the type to this class set if necessary:
		if (!(thisTypeClassSet.contains(t.o))){
			Debugger.log("Added type " + t.o + " at class set " + thisTypeClassSet);
			thisTypeClassSet.add(t.o);
		}
		// If the node had another class set before, fuse
		Long thisNodeClassSetID = n2cs.get(new Long(t.s));

		if (thisNodeClassSetID != null){ // the class sets need to be fused
			ArrayList<Long> thisNodeClassSet = cs.get(thisNodeClassSetID);
			if (thisNodeClassSetID < classSetIDForThisType){
				Debugger.log("(1) Fusing class set " + classSetIDForThisType + " into " + thisNodeClassSetID);
				// unify both into thisNodeClassSetID:
				// - thisNodeClassSet gets all the properties of thisTypeCS
				for (Long cl: thisTypeClassSet){
					thisNodeClassSet.add(cl);
				}
				// in this case, we are updating the class set of the o class
				c2cs.put(t.o, thisNodeClassSetID);
				// and the class set (thus, representative) of this node: 
				n2cs.replaceValue(classSetIDForThisType, thisNodeClassSetID);
				//n2cs.put(t.s, thisNodeClassSetID);
				// all the nodes previously attached to thisNodeClassSetID need to change
				cs.fuseKeyInto(classSetIDForThisType, thisNodeClassSetID);
			}
			if (thisNodeClassSetID > classSetIDForThisType){
				Debugger.log("(2) Fusing class set " + thisNodeClassSetID + " into " + classSetIDForThisType);
				// unify both into classSetIDForThisType:
				// - classSetIDForThisType gets all the properties of thisNodeClassSet
				for (Long cl: thisNodeClassSet){
					thisTypeClassSet.add(cl);
				}
				// - replace the class set in cs:
				cs.remove(thisNodeClassSetID);
				n2cs.replaceValue(thisNodeClassSetID, classSetIDForThisType);
				//n2cs.put(t.s, classSetIDForThisType);
				// all the nodes previously attached to thisNodeClassSetID need to change
				cs.fuseKeyInto(thisNodeClassSetID, classSetIDForThisType);
			}
		}
		else{ // the node did not have another class set before
			n2cs.put(new Long(t.s), classSetIDForThisType);
		}
		//display();
		this.numberOfTypeTriplesRead ++; 
	}
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName()); 
	}
}
