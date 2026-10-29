import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminExportComponent } from './admin-export.component';

describe('AdminExportComponent', () => {
  let component: AdminExportComponent;
  let fixture: ComponentFixture<AdminExportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminExportComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminExportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
