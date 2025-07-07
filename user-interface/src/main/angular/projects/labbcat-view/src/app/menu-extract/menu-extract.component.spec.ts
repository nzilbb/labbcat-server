import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MenuExtractComponent } from './menu-extract.component';

describe('MenuExtractComponent', () => {
  let component: MenuExtractComponent;
  let fixture: ComponentFixture<MenuExtractComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MenuExtractComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(MenuExtractComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
