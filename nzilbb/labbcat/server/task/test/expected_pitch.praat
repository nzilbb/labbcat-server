Open long sound file... /home/robert/nzilbb/labbcat-server/nzilbb/labbcat/server/task/test/text.wav
Rename... soundfile
select LongSound soundfile
Extract part... 0.1 0.2 0
Rename... sample0
select Sound sample0
pitchFloor = 60
voicingThreshold = 0.5
pitchCeiling = 500
To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling
result = Get minimum... 0 0.1 Hertz Parabolic
print 'result:0'
printline
result = Get mean... 0 0.1 Hertz
print 'result:0'
printline
result = Get maximum... 0 0.1 Hertz Parabolic
print 'result:0'
printline
Remove
select Sound sample0
Remove
select LongSound soundfile
Extract part... 3 4 0
Rename... sample1
select Sound sample1
pitchFloor = 60
voicingThreshold = 0.5
pitchCeiling = 500
To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling
result = Get minimum... 0 1 Hertz Parabolic
print 'result:0'
printline
result = Get mean... 0 1 Hertz
print 'result:0'
printline
result = Get maximum... 0 1 Hertz Parabolic
print 'result:0'
printline
Remove
select Sound sample1
Remove