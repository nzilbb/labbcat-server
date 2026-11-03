import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnnotationsUploadComponent } from './annotations-upload.component';

describe('AnnotationsUploadComponent', () => {
  let component: AnnotationsUploadComponent;
  let fixture: ComponentFixture<AnnotationsUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnnotationsUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AnnotationsUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
