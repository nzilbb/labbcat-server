import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CorpusComponent } from './corpus.component';

describe('CorpusComponent', () => {
  let component: CorpusComponent;
  let fixture: ComponentFixture<CorpusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CorpusComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(CorpusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
