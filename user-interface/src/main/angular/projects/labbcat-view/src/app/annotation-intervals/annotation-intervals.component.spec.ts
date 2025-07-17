import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnnotationIntervalsComponent } from './annotation-intervals.component';

describe('AnnotationIntervalsComponent', () => {
  let component: AnnotationIntervalsComponent;
  let fixture: ComponentFixture<AnnotationIntervalsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnnotationIntervalsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AnnotationIntervalsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
