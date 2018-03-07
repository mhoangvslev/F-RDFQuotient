package fr.inria.cedar.quotientSummary.triplegenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class RandomTripleGenerator {

	long dataTripleNumber;
	long typeTripleNumber; 
	long maxURI; 
	long classNumber;
	long propertyNumber;
	long typeProperty = 2;
	
	String dataTriplesFile; 
	String typeTriplesFile;
	
	Random r; 
	
	public RandomTripleGenerator(String typeFile, String dataFile, Long typeNo, Long dataNo, Long maxURI, Long classNo, long propertyNo) {
		this.typeTriplesFile = typeFile;
		this.dataTriplesFile = dataFile;
		this.typeTripleNumber = typeNo;
		this.dataTripleNumber = dataNo; 
		this.maxURI = maxURI;
		this.classNumber = classNo;
		this.propertyNumber = propertyNo; 
	}

	void generateTriples(){
		r  = new Random();
		try(BufferedWriter dataBw = new BufferedWriter(new FileWriter(dataTriplesFile));
			BufferedWriter typeBw = new BufferedWriter(new FileWriter(typeTriplesFile))) {

			for (long i = 0; i < typeTripleNumber; i ++){
				long s = (long)(r.nextDouble() * maxURI); 
				long p = typeProperty;
				long o = (long)(r.nextDouble() * classNumber); 
				typeBw.write(s + " " + p + " " + o + "\n");
			}
			
			for (long i = 0; i < dataTripleNumber; i ++){
				long s = (long)(r.nextDouble() * maxURI);
				long p = typeProperty;
				while (p == typeProperty){
					p = (long)(r.nextDouble() * propertyNumber); // so that we do not generate type triples in the data triples set
				}
				long o = (long)(r.nextDouble() * maxURI);
				dataBw.write(s + " " + p + " " + o + "\n");
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args){
		if (args.length < 7){
			System.out.println("Usage: RandomTripleGenerator typeTriplesFile dataTriplesFile typeTriplesNumber dataTriplesNumber maxURI classNumber propertyNumber"); 
		}
		RandomTripleGenerator gen = new RandomTripleGenerator(args[0], args[1], new Long(args[2]), new Long(args[3]), new Long(args[4]), new Long(args[5]), new Long(args[6])); 
		gen.generateTriples();
	}
	
}
