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
Extract part... 0 0.3 0
Rename... sample0
select Sound sample0
@trackAutoselect: selected(), dir$, 5000, 4500, steps, coefficients, formants, method$, image, selected(), current_view, max_plot, out_formant, out_table, out_all
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
Remove
select LongSound soundfile
Extract part... 2.9 4.1 0
Rename... sample1
select Sound sample1
@trackAutoselect: selected(), dir$, 5000, 4500, steps, coefficients, formants, method$, image, selected(), current_view, max_plot, out_formant, out_table, out_all
result = Get value at time... 1 0.6 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 0.6 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 0.6 Hertz Linear
print 'result:0'
printline
result = Get value at time... 1 0.6 Hertz Linear
print 'result:0'
printline
result = Get value at time... 2 0.6 Hertz Linear
print 'result:0'
printline
result = Get value at time... 3 0.6 Hertz Linear
print 'result:0'
printline
Remove
select Sound sample1
Remove