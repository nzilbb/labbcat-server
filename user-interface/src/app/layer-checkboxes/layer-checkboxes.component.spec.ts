import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { LayerCheckboxesComponent } from './layer-checkboxes.component';

describe('LayerCheckboxesComponent', () => {
  let component: LayerCheckboxesComponent;
  let fixture: ComponentFixture<LayerCheckboxesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ LayerCheckboxesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LayerCheckboxesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
