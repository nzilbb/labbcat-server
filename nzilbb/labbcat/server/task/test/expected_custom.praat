Open long sound file... /home/robert/nzilbb/labbcat-server/nzilbb/labbcat/server/task/test/text.wav
Rename... soundfile
select LongSound soundfile
Extract part... 0 0.7 0
Rename... sample0
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