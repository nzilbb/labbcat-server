import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'duration'
})
export class DurationPipe implements PipeTransform {    
    transform(value: number): unknown {
        const minutes = Math.floor(value / 60);
        const wholeSeconds = (value - minutes * 60).toFixed()
        const possibleLeadingZero = (""+wholeSeconds).length < 2?"0":""
        return minutes + ":" + possibleLeadingZero + (value - minutes * 60).toFixed(3);
    }    
}
