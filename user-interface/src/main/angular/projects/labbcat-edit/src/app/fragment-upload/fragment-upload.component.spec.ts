import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FragmentUploadComponent } from './fragment-upload.component';

describe('FragmentUploadComponent', () => {
  let component: FragmentUploadComponent;
  let fixture: ComponentFixture<FragmentUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FragmentUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(FragmentUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
