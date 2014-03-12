GCode-Mixer
===========

Program for mixing multiple gcode files into one. 

### Current Features
 * Header and end files with alternating layers between.
 * Automatic detection of z-lift usage and corrects output z-target heights.

Editor used: eclipse

### Planned Features
 * Support for specifying individual height intervals for each layer.

### Usage
```
java Main
          (-s|--source) <source> (-t|--step) <step> [(-c|--layerchange) <layerchange>] (-l|--layer) <layer> [--lift <lift>] (-o|--out) <out> [-i|--info]

  (-s|--source) <source>
        <filename>:<id>

  (-t|--step) <step>
        <double>

  [(-c|--layerchange) <layerchange>]
        <slic3r layer change output> (default: ;Layer change)

  (-l|--layer) <layer>
        [<id>;]<from_range>[:<to_range>[,<from_range>:<to_range>]]

  [--lift <lift>]
        <double>

  (-o|--out) <out>
        reads sources, prints their information and stops (default: out.gco)

  [-i|--info]

Example: --step 0.3 --source in.gcode:a --source in.gcode:b --layer a;0-5,6-7:6-20,21,29:30 --layer b;22-29
```

Example: `java -jar GCodeMixer.jar --step 0.3 --source orig.gcode:test --layer test;0-5,6-7:6-20,21,22:22-29,29:30`

Results in *out.gco* that begins with layer 0 to 5 of *orig.gcode*, layer 6 to 20 are copies of orig layers 6,7 in a rotating fashion, layer 21 is a copy of orig layer 21, layer 22 to 29 are copies of orig layer 22, layer 30 is a copy of orig layer 29.

