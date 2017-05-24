SynthDef.new("test", {
    Out.ar(0, SinOsc.ar(240, 0, 0.2));
}).writeDefFile;
