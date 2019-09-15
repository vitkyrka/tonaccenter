#!/usr/bin/env python3

import parselmouth
import json

fn = sys.argv[1]
snd = parselmouth.Sound(fn)

pitch = snd.to_pitch()
pitchdata = [(x, y) for (x, y)  in zip(pitch.xs(), pitch.selected_array['frequency'])]

intensity = snd.to_intensity()
intensitydata = [(x, y[0]) for (x, y)  in zip(intensity.xs(), intensity.values.T)]

with open(fn.replace(".wav", ".json"), "w") as f:
    f.write(json.dumps({"pitch": pitchdata, "intensity": intensitydata}, indent=4))
