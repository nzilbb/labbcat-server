import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MatchesUploadComponent } from './matches-upload.component';

describe('MatchesUploadComponent', () => {
  let component: MatchesUploadComponent;
  let fixture: ComponentFixture<MatchesUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatchesUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(MatchesUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
