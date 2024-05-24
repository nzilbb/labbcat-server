import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AllUtterancesComponent } from './all-utterances.component';

describe('AllUtterancesComponent', () => {
  let component: AllUtterancesComponent;
  let fixture: ComponentFixture<AllUtterancesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AllUtterancesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AllUtterancesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
