import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'duration'
})
export class DurationPipe implements PipeTransform {    
    transform(value: number): unknown {
        const hours = Math.floor(value / 3600);
        let minutes = Math.floor(value / 60);
        const wholeSeconds = (value - minutes * 60).toFixed()
        if (hours >= 1) {
            minutes = minutes - hours * 60;
        }
        const possibleLeadingZero = (""+wholeSeconds).length < 2?"0":""
        return (hours >= 1 ? hours.toFixed().padStart(2, "0") + ":" : "") +
            minutes.toFixed().padStart(2, "0") + ":" + possibleLeadingZero +
            (value - hours * 3600 - minutes * 60).toFixed(3);
    }
}
