Open long sound file... /home/robert/nzilbb/labbcat-server/nzilbb/labbcat/server/task/test/text.wav
Rename... soundfile
# FastTrack:
include utils/trackAutoselectProcedure.praat
@getSettings
time_step = 0.002
basis_functions$ = "dct"
error_method$ = "mae"
method$ = "burg"
enable_F1_frequency_heuristic = 1
maximum_F1_frequency_value = 1200
enable_F1_bandwidth_heuristic = 0
maximum_F1_bandwidth_value = 500
enable_F2_bandwidth_heuristic = 0
maximum_F2_bandwidth_value = 600
enable_F3_bandwidth_heuristic = 0
maximum_F3_bandwidth_value = 900
enable_F4_frequency_heuristic = 1
minimum_F4_frequency_value = 2900
enable_rhotic_heuristic = 1
enable_F3F4_proximity_heuristic = 1
output_bandwidth = 1
output_predictions = 1
output_pitch = 1
output_intensity = 1
output_harmonicity = 1
output_normalized_time = 1
dir$ = "/home/robert/nzilbb/labbcat-server/nzilbb/labbcat/server/task/test"
steps = 20
coefficients = 5
formants = 3
out_formant = 2
image = 0
max_plot = 4000
out_table = 0
out_all = 0
current_view = 0
select LongSound soundfile
Extract part... 0 0.7 0
Rename... sample0
select Sound sample0
@trackAutoselect: selected(), dir$, 450, 6500, steps, coefficients, formants, method$, image, selected(), current_view, max_plot, out_formant, out_table, out_all
result = Get value at time... 1 0.15 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 0.15 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 0.15 Hertz Linear
print 'result:0'
printline
result = Get value at time... 1 0.15 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 0.15 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 0.15 Hertz Linear
print 'result:0'
printline
Remove
select Sound sample0
pitchFloor = 30
voicingThreshold = 0.4
pitchCeiling = 250
To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling
result = Get minimum... 0.1 0.2 Hertz Parabolic
print 'result:0'
printline
result = Get mean... 0.1 0.2 Hertz
print 'result:0'
printline
result = Get maximum... 0.1 0.2 Hertz Parabolic
print 'result:0'
printline
Remove
select Sound sample0
intensityPitchFloor = 40
To Intensity... intensityPitchFloor 0 yes
result = Get maximum... 0.1 0.2 Parabolic
print 'result'
printline
Remove
select Sound sample0
To Spectrum... yes
result = Get centre of gravity... 1
print 'result:0'
printline
result = Get centre of gravity... 2
print 'result:0'
printline
result = Get centre of gravity... 23
print 'result:0'
printline
Remove
######### CUSTOM SCRIPT STARTS HERE #########
sampleStartTime = 0
sampleEndTime = 0.7
sampleDuration = 0.7
windowOffset = 0.5
windowAbsoluteStart = 0
windowAbsoluteEnd = 0.7
windowDuration = 0.7
targetAbsoluteStart = 0.1
targetAbsoluteEnd = 0.2
targetStart = 0.1
targetEnd = 0.2
targetDuration = 0.1
sampleNumber = 0
sampleName$ = "sample0"
participant_gender$ = "F"
select Sound sample0
# get centre of gravity and spread from spectrum
spectrum = To Spectrum... yes
# filter it
Filter (pass Hann band)... 1000 22000 100
# get centre of gravity
cog = Get centre of gravity... 2
# extract the result back out into a CSV column called 'cog'
print 'cog:0' 'newline$'
# tidy up objects
select spectrum
Remove
##########  CUSTOM SCRIPT ENDS HERE  #########
select Sound sample0
Remove
select LongSound soundfile
Extract part... 2.5 4.5 0
Rename... sample1
select Sound sample1
@trackAutoselect: selected(), dir$, 450, 6500, steps, coefficients, formants, method$, image, selected(), current_view, max_plot, out_formant, out_table, out_all
result = Get value at time... 1 1 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 1 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 1 Hertz Linear
print 'result:0'
printline
result = Get value at time... 1 1 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 1 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 1 Hertz Linear
print 'result:0'
printline
Remove
select Sound sample1
pitchFloor = 30
voicingThreshold = 0.4
pitchCeiling = 250
To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling
result = Get minimum... 0.5 1.5 Hertz Parabolic
print 'result:0'
printline
result = Get mean... 0.5 1.5 Hertz
print 'result:0'
printline
result = Get maximum... 0.5 1.5 Hertz Parabolic
print 'result:0'
printline
Remove
select Sound sample1
intensityPitchFloor = 40
To Intensity... intensityPitchFloor 0 yes
result = Get maximum... 0.5 1.5 Parabolic
print 'result'
printline
Remove
select Sound sample1
To Spectrum... yes
result = Get centre of gravity... 1
print 'result:0'
printline
result = Get centre of gravity... 2
print 'result:0'
printline
result = Get centre of gravity... 23
print 'result:0'
printline
Remove
######### CUSTOM SCRIPT STARTS HERE #########
sampleStartTime = 2.5
sampleEndTime = 4.5
sampleDuration = 2
windowOffset = 0.5
windowAbsoluteStart = 2.5
windowAbsoluteEnd = 4.5
windowDuration = 2
targetAbsoluteStart = 3
targetAbsoluteEnd = 4
targetStart = 0.5
targetEnd = 1.5
targetDuration = 1
sampleNumber = 1
sampleName$ = "sample1"
participant_gender$ = "F"
select Sound sample1
# get centre of gravity and spread from spectrum
spectrum = To Spectrum... yes
# filter it
Filter (pass Hann band)... 1000 22000 100
# get centre of gravity
cog = Get centre of gravity... 2
# extract the result back out into a CSV column called 'cog'
print 'cog:0' 'newline$'
# tidy up objects
select spectrum
Remove
##########  CUSTOM SCRIPT ENDS HERE  #########
select Sound sample1
Remove