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
          (-h|--head) <head> (-t|--tail) <tail> (-b|--begin) <begin>
          (-e|--end) <end> (-s|--step) <step> (-l|--layer) <layer> [--lift <lift>]

  (-h|--head) <head>
        (default: head.txt)

  (-t|--tail) <tail>
        (default: tail.txt)

  (-b|--begin) <begin>

  (-e|--end) <end>

  (-s|--step) <step>

  (-l|--layer) <layer>

  [--lift <lift>]
        (default: 0.1)
```

Example usage: java Main --begin 1.5 --end 25.5 --step 0.3 -l "layer a.txt" -l "layer b.txt" -l "layer c.txt"
Results in out.gco that begins with the contents of head.txt and between 1.5-25.5mm uses layer a/b/c in a rotating fashion with layer height 0.3.

