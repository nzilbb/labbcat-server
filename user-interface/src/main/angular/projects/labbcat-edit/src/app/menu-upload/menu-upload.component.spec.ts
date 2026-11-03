import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MenuUploadComponent } from './menu-upload.component';

describe('MenuUploadComponent', () => {
  let component: MenuUploadComponent;
  let fixture: ComponentFixture<MenuUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MenuUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(MenuUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
