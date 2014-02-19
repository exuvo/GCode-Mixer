import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;

public class Main {

	static String head, tail;
	static double start, end, step, lift;
	static List<Layer> layers = new ArrayList<Layer>();

	public static void main(String[] args) throws IOException {
		JSAP jsap = new JSAP();
        arguments(jsap);
        
        JSAPResult config = jsap.parse(args);
        // check whether the command line was valid, and if it wasn't, display usage information and exit.
        if (!config.success()) {
            System.err.println();
         // print out specific error messages describing the problems
            // with the command line, THEN print usage, THEN print full
            // help.  This is called "beating the user with a clue stick."
            for (Iterator<?> errs = config.getErrorMessageIterator();
                    errs.hasNext();) {
                System.err.println("Error: " + errs.next());
            }
            
            System.err.println();
            System.err.println("Usage: java " + Main.class.getName());
            System.err.println("                " + jsap.getUsage());
            System.err.println();
            // show full help as well
            System.err.println(jsap.getHelp());
            System.exit(1);
        }
        
		head = readFile(config.getString("head"));
		tail = readFile(config.getString("tail"));
		
		for(String file : config.getStringArray("layer")){
			Layer l = new Layer();
			l.gcode = readFile(file);
			
			Scanner s = new Scanner(l.gcode);
			Matcher m = Pattern.compile("X[0-9]+\\.[0-9]+ Y[0-9]+\\.[0-9]+").matcher("");
			String line;
			do {
				line = s.nextLine();
				m.reset(line);
			}while(!(m.find()));
			l.pos = m.group();
			
			l.zlift = l.gcode.contains("Z");
			if(l.zlift){
				System.out.println("Z-lift detected");
				Set<String> zmovement = new HashSet<String>();
				m = Pattern.compile("Z[0-9]+\\.[0-9]+").matcher("");
				s.close();
				s = new Scanner(l.gcode);
				
				while(s.hasNext()){
					line = s.nextLine();
					m.reset(line);
					if(m.find()){
						zmovement.add(m.group());
					}
				}
				
				s.close();
				
				if(zmovement.size() != 2){
					throw new IllegalArgumentException("Expected 2 different Z heights, found " + zmovement.size());
				}
				
				Iterator<String> it = zmovement.iterator();
				String z1s = it.next();
				String z2s = it.next();
				double z1 = Double.parseDouble(z1s.replace("Z", ""));
				double z2 = Double.parseDouble(z2s.replace("Z", ""));
				
				if(z1 < z2){
					l.z1 = z1s;
					l.z2 = z2s;
				}else{
					l.z1 = z2s;
					l.z2 = z1s;
				}
			}
			
			layers.add(l);
		}
		
		PrintWriter f = new PrintWriter("out.gco", "UTF-8");
		f.println(head);

		double pos = config.getDouble("begin");
		end = config.getDouble("end");
		step = config.getDouble("step");
		lift = config.getDouble("lift");
		int current_layer = 0;

		while (pos < end + step/2) {
			Layer l = layers.get(current_layer);
			System.out.format("Mixing layer %.3f mm\n", pos);
			f.format("G1 Z%.3f F9000.000\n", pos + lift);
			f.format("G1 %s F9000.000\n", l.pos);
			f.format("G1 Z%.3f F9000.000\n", pos);
			
			if(l.zlift){
				String z1 = String.format("Z%.3f", pos);
				String z2 = String.format("Z%.3f", pos + lift);
				f.println(l.gcode.replace(l.z1, z1).replace(l.z2, z2));
			}else{
				f.println(l.gcode);
			}
			
			if(++current_layer+1 > layers.size()){
				current_layer = 0;
			}
			
			pos += step;
		}
		
		f.println(tail);
		f.close();
		System.out.println("Done");
	}
	
private static final void arguments(JSAP jsap){
		
		FlaggedOption head = new FlaggedOption("head")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault("head.txt")
			.setRequired(true)
			.setShortFlag('h')
			.setLongFlag("head");
		head.setHelp("");
		
		FlaggedOption tail = new FlaggedOption("tail")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault("tail.txt")
			.setRequired(true)
			.setShortFlag('t')
			.setLongFlag("tail");
		tail.setHelp("");
		
		FlaggedOption begin = new FlaggedOption("begin")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('b')
			.setLongFlag("begin");
		begin.setHelp("");
		
		FlaggedOption end = new FlaggedOption("end")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('e')
			.setLongFlag("end");
		end.setHelp("");
		
		FlaggedOption step = new FlaggedOption("step")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('s')
			.setLongFlag("step");
		step.setHelp("");
		
		FlaggedOption lift = new FlaggedOption("lift")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault("0.1")
			.setRequired(false)
			.setLongFlag("lift");
		lift.setHelp("");
		
		FlaggedOption layer = new FlaggedOption("layer")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('l')
			.setLongFlag("layer")
			.setAllowMultipleDeclarations(true);
		layer.setHelp("");
		
		try {
			jsap.registerParameter(head);
			jsap.registerParameter(tail);
			jsap.registerParameter(begin);
			jsap.registerParameter(end);
			jsap.registerParameter(step);
			jsap.registerParameter(layer);
			jsap.registerParameter(lift);
		} catch (JSAPException e) {
			System.err.println("JSAP: Failed to register parameters due to: " + e);
		}
	}

	static String readFile(String path) throws IOException {
		System.out.println("Reading file '" + path + "'");
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(encoded)).toString();
	}
	
	static class Layer {
		boolean zlift;
		String gcode, pos, z1, z2;
	}

}
