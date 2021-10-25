Open long sound file... /home/robert/nzilbb/labbcat-server/nzilbb/labbcat/server/task/test/text.wav
Rename... soundfile
select LongSound soundfile
Extract part... 0.1 0.2 0
Rename... sample0
select Sound sample0
To Spectrum... yes
result = Get centre of gravity... 1
print 'result:0'
printline
result = Get centre of gravity... 2
print 'result:0'
printline
result = Get centre of gravity... 2/3
print 'result:0'
printline
Remove
select Sound sample0
Remove
select LongSound soundfile
Extract part... 3 4 0
Rename... sample1
select Sound sample1
To Spectrum... yes
result = Get centre of gravity... 1
print 'result:0'
printline
result = Get centre of gravity... 2
print 'result:0'
printline
result = Get centre of gravity... 2/3
print 'result:0'
printline
Remove
select Sound sample1
Remove