SynthDef.new("test", {
	Out.ar(0, SinOsc.ar(240, 0, 0.2));
}).writeDefFile;

SynthDef.new("test2", {
	arg freq=240;
	Out.ar(0, SinOsc.ar([freq, freq], 0, 0.2));
}).writeDefFile;
