import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewMenuComponent } from './view-menu.component';

describe('ViewMenuComponent', () => {
  let component: ViewMenuComponent;
  let fixture: ComponentFixture<ViewMenuComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ViewMenuComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ViewMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
