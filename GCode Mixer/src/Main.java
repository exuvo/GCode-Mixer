import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;

public class Main {

	static List<LayerSource> sources = new ArrayList<LayerSource>();
	static List<LayerGroup> layers = new ArrayList<LayerGroup>();

	public static void main(String[] args) throws IOException {
		System.out.println(arrayToString(args));
		JSAP jsap = new JSAP();
        arguments(jsap);
        
        JSAPResult config = jsap.parse(args);
        
        if(config.getBoolean("info")){//Fix for required parameters
        	String[] args2 = Arrays.copyOfRange(args, 0, args.length+2);
       		args2[args.length] = "--step=0";
       		args2[args.length+1] = "--layer=0";
        	config = jsap.parse(args2);
		}
        
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
            System.err.println("Example: --step 0.3 --source in.gcode:a --source in.gcode:b --layer a;0-5,6-7:6-20,21,29:30 --layer b;22-29");
            System.exit(1);
        }
        
        String layerChange = config.getString("layerchange");
        
		for(String potentialSource : config.getStringArray("source")){
			LayerSource source = new LayerSource();
			String file;
			
			if(Pattern.matches("[^:]+:\\w+", potentialSource)){
				String split[] = potentialSource.split(":");
				if(split.length != 2){
					throw new IllegalArgumentException("Expected 2, got " + split.length);
				}
				file = split[0];
				source.name = split[1];
				System.out.print("Source '" + source.name + "': ");
			}else if(Pattern.matches("[^:]+", potentialSource)){
				file = potentialSource;
				source.name = "";
			}else{
				throw new IllegalArgumentException("Source parameter not valid: \"" + potentialSource + "\"");
			}
			
			String gcode = readFile(file);
			
			if(gcode.contains(layerChange)){
				String potentialLayers[] = gcode.split(layerChange);
				System.out.print(", Found " + potentialLayers.length + " layers.");
				for(int i=0; i<potentialLayers.length; i++){
					String layer = potentialLayers[i];
					Scanner scan = new Scanner(layer);
					String line;
					do {
						line = scan.nextLine();
					}while(scan.hasNextLine());
					scan.close();
					
					if(line.contains("Z")){
						if(i+1 <potentialLayers.length){
							potentialLayers[i+1] = line + potentialLayers[i+1];
						}
						layer = layer.substring(0, layer.lastIndexOf(line));
					}
					
					Layer l = new Layer(layer);
					source.layers.add(l);
					if(l.zlift && !source.zlift){
						System.out.println(" Z-lift detected");
						source.zlift = true;
					}
				}
				if(!source.zlift){
					System.out.println();
				}
			}else{
				System.out.print(", Found no layerchange comment, assuming whole file is one layer.");
				if(gcode.contains("Z")){
					System.out.println(" Z-lift detected");
					source.zlift = true;
				}else{
					System.out.println();
				}
				source.layers.add(new Layer(gcode));
			}
			
			sources.add(source);
		}
		
		if(sources.size() > 1){
			for(LayerSource source : sources){
				if(source.equals("")){
					throw new IllegalArgumentException("Ambigous source names, must name all sources when using multiple sources");
				}
			}
			
			for(LayerSource source : sources){
				for(LayerSource source2 : sources){
					if(source != source2 && source.equals(source2)){
						throw new IllegalArgumentException("Ambigous source name, appears twice: \"" + source + "\"");
					}
				}
			}
		}
		
		if(config.getBoolean("info")){
			System.out.println("Info enabled, stopping here.");
			System.exit(0);
		}
		
		for(String layer : config.getStringArray("layer")){
			LayerSource source;
			
			if(Pattern.matches("\\w+;((\\d+-\\d+:\\d+-\\d+|\\d+-\\d+|\\d+:\\d+|\\d+:\\d+-\\d+|\\d+),)*?(\\d+-\\d+:\\d+-\\d+|\\d+-\\d+|\\d+:\\d+|\\d+:\\d+-\\d+|\\d+)", layer)){
				source = getSource(layer.substring(0, layer.indexOf(";")));
				layer = layer.substring(layer.indexOf(";") + 1, layer.length());
			}else if(Pattern.matches("((\\d+-\\d+:\\d+-\\d+|\\d+-\\d+|\\d+:\\d+|\\d+:\\d+-\\d+|\\d+),)*?(\\d+-\\d+:\\d+-\\d+|\\d+-\\d+|\\d+:\\d+|\\d+:\\d+-\\d+|\\d+)", layer)){
				if(sources.size() > 1){
					throw new IllegalArgumentException("Ambigous layer source name, must name all sources when using multiple sources");
				}
				source = sources.get(0);
			}else{
				throw new IllegalArgumentException("Layer parameter not valid: \"" + layer + "\"");
			}
			
			String split[] = layer.split(",");
			for(String l : split){
				LayerGroup group = new LayerGroup();
				group.source = source;
				
				if(Pattern.matches("\\d+-\\d+:\\d+-\\d+", l)){
					String colon[] = l.split(":");
					String leftminus[] = colon[0].split("-");
					group.fromStart = Integer.parseInt(leftminus[0]);
					group.fromEnd = Integer.parseInt(leftminus[1]);
					String rightminus[] = colon[1].split("-");
					group.toStart = Integer.parseInt(rightminus[0]);
					group.toEnd = Integer.parseInt(rightminus[1]);
				}else if(Pattern.matches("\\d+-\\d+", l)){
					String minus[] = l.split("-");
					group.fromStart = group.toStart = Integer.parseInt(minus[0]);
					group.fromEnd = group.toEnd = Integer.parseInt(minus[1]);
				}else if(Pattern.matches("\\d+:\\d+", l)){
					String colon[] = l.split(":");
					group.fromStart = group.fromEnd = Integer.parseInt(colon[0]);
					group.toStart = group.toEnd = Integer.parseInt(colon[1]);
				}else if(Pattern.matches("\\d+:\\d+-\\d+", l)){
					String colon[] = l.split(":");
					group.fromStart = group.fromEnd = Integer.parseInt(colon[0]);
					String minus[] = colon[1].split("-");
					group.toStart = Integer.parseInt(minus[0]);
					group.toEnd = Integer.parseInt(minus[1]);
				}else if(Pattern.matches("\\d+", l)){
					group.fromStart = group.fromEnd = group.toStart = group.toEnd = Integer.parseInt(l);
				}else{
					throw new IllegalArgumentException("Layer parameter not valid: \"" + l + "\" in \"" + layer + "\"");
				}
				
				if(group.toStart > group.toEnd){
					throw new IllegalArgumentException("Layer range not valid, toStart is larger than toEnd: \"" + l + "\" in \"" + layer + "\"");
				}
				
				if(group.fromStart < 0 || group.fromEnd < 0 || group.toStart < 0 || group.toEnd < 0){
					throw new IllegalArgumentException("Layer range not valid, layers below 0: \"" + l + "\" in \"" + layer + "\"");
				}
				
				if(group.fromStart - 1 > source.layers.size() || group.fromEnd - 1 > source.layers.size()){
					throw new IllegalArgumentException("Layer range not valid, from is outside source: \"" + l + "\" in \"" + layer + "\"");
				}
				
//				System.out.println(group.source.name + ";" + group.fromStart + "-" + group.fromEnd + ":" + group.toStart + "-" + group.toEnd);
				layers.add(group);
			}
		}
		
		Collections.sort(layers);
		
		{//Check for overlapping or empty layers
			int max = -1;
			for(LayerGroup g : layers){
				max = Math.max(max, Math.max(g.toStart, g.toEnd));
			}
			
			boolean used[] = new boolean[max+1];
			for(LayerGroup g : layers){
				int top = Math.max(g.toStart, g.toEnd);
				int bot = Math.min(g.toStart, g.toEnd);
				for(int i= bot; i <= top; i++){
					if(used[i]){
						throw new IllegalArgumentException("Overlapping layer: " + i);
					}
					used[i] = true;
				}
			}
			
			for(int i=0; i<used.length; i++){
				if(!used[i]){
					throw new IllegalArgumentException("Empty layer: " + i);
				}
			}
		}
		
		String outFile = config.getString("out");
		System.out.println("Opening output file '" + outFile + "'");
		PrintWriter f = new PrintWriter(outFile, "UTF-8");
		f.println("; GCode Mixer: " + arrayToString(args));
		
		double step = config.getDouble("step");
		boolean forceLift = config.userSpecified("lift");
		double forcedlift = forceLift ? config.getDouble("lift") : 0;
		
		for(LayerGroup group : layers){
			int fromDirection = group.fromStart < group.fromEnd ? 1 : -1;
			int from = group.fromStart;
			
			for(int i = group.toStart; i <= group.toEnd; i++){
				Layer l = group.source.layers.get(from);
				double height = i * step;
				
				System.out.format("Mixing layer %d at %.2f mm from '%s':%d", i, height, group.source.name, from);
				f.println(layerChange);
				
				String z1 = String.format("Z%.3f", height);
				if(l.zlift){
					double lift = forceLift ? forcedlift : l.zdiff;
					String z2 = String.format("Z%.3f", height + lift);
					f.print(l.gcode.replace(l.zlow, z1).replace(l.zhigh, z2));
					System.out.format(" with %.2f zlift\n", lift);
				}else{
					if(forceLift){
						f.format("G1 Z%.3f F9000.000\n", height + forcedlift);
						System.out.format(" with %.2f layerchange-zlift\n", forcedlift);
					}else{
						System.out.println();
					}
					f.print(l.gcode.replace(l.zlow, z1));
				}
				
				from += fromDirection;
				if(fromDirection == 1){
					if(from > group.fromEnd){
						from = group.fromStart;
					}
				}else{
					if(from < group.fromEnd){
						from = group.fromStart;
					}
				}
			}
		}

		f.close();
		System.out.println("Done");
	}
	
	static String arrayToString(Object[] in){
		StringBuilder b = new StringBuilder();
		for(Object o : in){
			b.append(o);
			b.append(' ');
		}
		return b.toString();
	}
	
	private static LayerSource getSource(String name){
		for(LayerSource source : sources){
			if(source.name.equals(name)){
				return source;
			}
		}
		throw new IllegalArgumentException("Layer source not valid: \"" + name + "\"");
	}
	
	static String readFile(String path) throws IOException {
		System.out.print("Reading file '" + path + "'");
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(encoded)).toString();
	}
	
	private static final void arguments(JSAP jsap){
		
		FlaggedOption step = new FlaggedOption("step")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('t')
			.setLongFlag("step");
		step.setHelp("<double>");
		
		FlaggedOption lift = new FlaggedOption("lift")
			.setStringParser(JSAP.DOUBLE_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(false)
			.setLongFlag("lift");
		lift.setHelp("<double>");
		
		FlaggedOption source = new FlaggedOption("source")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('s')
			.setLongFlag("source")
			.setAllowMultipleDeclarations(true);
		source.setHelp("<filename>:<id>");
		
		FlaggedOption layer = new FlaggedOption("layer")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault(JSAP.NO_DEFAULT)
			.setRequired(true)
			.setShortFlag('l')
			.setLongFlag("layer")
			.setAllowMultipleDeclarations(true);
		layer.setHelp("[<id>;]<from_range>[:<to_range>[,<from_range>:<to_range>]]");
		
		FlaggedOption layerchange = new FlaggedOption("layerchange")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault(";Layer change")
			.setRequired(false)
			.setShortFlag('c')
			.setLongFlag("layerchange");
		layerchange.setHelp("<slic3r layer change output>");
		
		FlaggedOption out = new FlaggedOption("out")
			.setStringParser(JSAP.STRING_PARSER)
			.setDefault("out.gco")
			.setRequired(true)
			.setShortFlag('o')
			.setLongFlag("out");
		out.setHelp("<filename>");
		
		Switch info = new Switch("info")
			.setShortFlag('i')
			.setLongFlag("info");
		out.setHelp("reads sources, prints their information and stops");
		
		try {
			jsap.registerParameter(source);
			jsap.registerParameter(step);
			jsap.registerParameter(layerchange);
			jsap.registerParameter(layer);
			jsap.registerParameter(lift);
			jsap.registerParameter(out);
			jsap.registerParameter(info);
		} catch (JSAPException e) {
			System.err.println("JSAP: Failed to register parameters due to: " + e);
		}
	}

	static class Layer {
		boolean zlift;
		double zdiff;
		String gcode, zlow = "ZLOW", zhigh = "ZHIGH";
		
		public Layer(String potentialLayer){
			this.gcode = potentialLayer;
			
			zlift = gcode.contains("Z");
			if(zlift){
				Set<String> zmovement = new HashSet<String>();
				Matcher m = Pattern.compile("Z\\d+\\.\\d+").matcher("");
				Scanner s = new Scanner(gcode);
				
				while(s.hasNext()){
					String line = s.nextLine();
					m.reset(line);
					if(m.find()){
						zmovement.add(m.group());
					}
				}
				
				s.close();
				
				if(zmovement.size() == 1){//Not a true zlift, only initial layer height change
					zlift = false;
					return;
				}else if(zmovement.size() != 2){
					throw new IllegalArgumentException("Expected 2 different Z heights, found " + zmovement.size());
				}
				
				Iterator<String> it = zmovement.iterator();
				String z1s = it.next();
				String z2s = it.next();
				double z1d = Double.parseDouble(z1s.replace("Z", ""));
				double z2d = Double.parseDouble(z2s.replace("Z", ""));
				
				zdiff = Math.abs(z1d - z2d);
				
				if(z1d < z2d){
					gcode = gcode.replace(z1s, zlow).replace(z2s, zhigh);
				}else{
					gcode = gcode.replace(z2s, zlow).replace(z1s, zhigh);
				}
			}
		}
	}
	
	static class LayerSource {
		List<Layer> layers = new ArrayList<Layer>();
		String name;
		boolean zlift;
	}
	
	static class LayerGroup implements Comparable<LayerGroup>{
		LayerSource source;
		int fromStart, fromEnd, toStart, toEnd;
		
		@Override
		public int compareTo(LayerGroup o) {
			int min = Math.min(toStart, toEnd);
			int minO = Math.min(o.toStart, o.toEnd);
			return min - minO;
		}
	}
}
