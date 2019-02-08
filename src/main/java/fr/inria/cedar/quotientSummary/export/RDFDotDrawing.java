//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.export;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;

import fr.inria.cedar.quotientSummary.datastructures.DecodedTriple;

/**
 * This class makes vanilla (black-on-white) drawings of a set of decoded RDF triples using DOT.
 * @author ioanamanolescu
 *
 */
public class RDFDotDrawing {
	String pathToDot; 

	private static final Logger LOGGER = Logger.getLogger(RDFDotDrawing.class.getName());

	
	public RDFDotDrawing(String pathToDot){
		this.pathToDot = pathToDot; 
	}
	
	public void drawTripleCollection(BufferedWriter bw, Collection<DecodedTriple> coll) throws IOException{
		for (DecodedTriple t: coll){
			drawTriple(bw, t); 
		}
	}
	
	public void drawTriple(BufferedWriter bw, DecodedTriple t) throws IOException{
		HashSet<String> printedNodes = new HashSet<String>();
		
		String subject, property, object, subjectInDot, propertyInDot, objectInDot;
		// in all cases, edge labels are preserved:
		property = t.getProperty(); 
		propertyInDot = property.replaceAll("\"", "");
		//System.out.println("Property: " + property);
		//System.out.println("Data triple\n");
		subject = t.getSource(); 
		subjectInDot = subject.replaceAll("\"", "");
		if (!printedNodes.contains(subject)){
			bw.write("\"" + subjectInDot);
			bw.write("\" [style = filled, fillcolor=white, color=blue, fontcolor=black ];\n");
			printedNodes.add(subject); 
		}
		object = t.getObject(); 
		objectInDot = object.replaceAll("\"", "");
		if (!printedNodes.contains(object)){
			bw.write("\"" + objectInDot);
			bw.write("\" [style = filled, fillcolor=white, color=blue, fontcolor=black ];\n");
			printedNodes.add(object); 
		}
		// write the triple in all cases:
		bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [label=\"" + propertyInDot);
		bw.write("\"];\n");
	}
	
	public void printRDFTriples(Collection<DecodedTriple> coll){
		long code = System.currentTimeMillis(); 
		String dotFileName = "rdf-" + code + ".dot"; 
		//LOGGER.debug("Trying to build DOT file:" + dotFileName + " out of " + coll.size() + "triples");
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)));
			bw.write("digraph g" + code + "{");
			for (DecodedTriple t: coll){
				drawTriple(bw, t); 
			}
			bw.write("}\n");
			bw.close();
		}
		catch(IOException ioe){
			throw new IllegalStateException("Unable to write DOT file"); 
		}	
		String pngFileName = "rdf-" + code + ".png"; 
		//LOGGER.debug("Trying to draw PNG file:" + pngFileName);
		
		try{
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + pngFileName);
			LOGGER.info("Drawn RDF in " + pngFileName); 
			
		}
		catch(Exception e){
			LOGGER.info("Unable to produce DOT drawing, check Dot path in RDFDotDrawing " + e); 
		}
	}
	/**
	 * Top method which can be called from anywhere to obtain just a simple plot.
	 * @param pathToNt
	 * @param pathToDot
	 */
	public static void printRDFFromFile(String pathToNt, String pathToDot) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(pathToNt)); 
			ArrayList<DecodedTriple> triples = new ArrayList<DecodedTriple>();
			while (br.ready()) {
				String line = br.readLine();
				String[] components = line.split(" ");
				DecodedTriple dt = new DecodedTriple(components[0], components[1], components[2]);
				triples.add(dt);
			}
			br.close();
			RDFDotDrawing rdd = new RDFDotDrawing(pathToDot);
			rdd.printRDFTriples(triples);
		}
		catch(Exception e) {
			LOGGER.info("Unable to plot RDF file using DOT: " + e);
		}
	}
}
