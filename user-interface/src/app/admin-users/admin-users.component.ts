import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { Response } from '../response';
import { User } from '../user';
import { Role } from '../role';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-users',
  templateUrl: './admin-users.component.html',
  styleUrls: ['./admin-users.component.css']
})
export class AdminUsersComponent extends AdminComponent implements OnInit {
    roles: Role[];
    rows: User[];

    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private router: Router
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readRoles().then((roles) => {
            this.roles = roles;
            this.readRows().then((users) => {
                this.rows = users;
            });
        });
    }
    
    readRoles(): Promise<Role[]> {
        return new Promise<Role[]>((resolve, reject) => {
            this.labbcatService.labbcat.readRoles((roles, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                resolve(roles as Role[]);
            });
        });
    }
    
    readRows(): Promise<User[]> {
        return new Promise<User[]>((resolve, reject) => {
            this.labbcatService.labbcat.readUsers((users, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                for (let user of users) {
                    if (user.user == this.labbcatService.labbcat.username) user._cantDelete = "Cannot delete yourself";
                }
                resolve(users as User[]);
            });
        });
    }

    toggleRole(user: User, role: string): void {
        if (user.roles.includes(role)) {
            // remove it
            user.roles = user.roles.filter(r => { return r !== role;});
        } else { // not already ticked
            // add it
            user.roles.push(role);
        }
        this.onChange(user);
    }

    onChange(row: User) {
        row._changed = this.changed = true;        
    }
    
    creating = false;
    createRow(user: string, email: string): boolean {
        // after creating the user, we're going to navigate to the password page
        // so before we do that, we want to save any other changes on this page
        this.updateChangedRows();

        // now create the user...
        this.creating = true;
        this.labbcatService.labbcat.createUser(
            user, email, true, [ "view" ],
            (row, errors, messages) => {
                this.creating = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                // update the model with the field returned
                if (row) {
                    this.rows.push(row as User);
                    this.updateChangedFlag();
                    this.router.navigate(["admin","users", row.user]);
                }
            });
        return true;
    }

    deleteRow(row: User) {
        row._deleting = true;
        if (confirm(`Are you sure you want to delete ${row.user}`)) {
            this.labbcatService.labbcat.deleteUser(row.user, (model, errors, messages) => {
                row._deleting = false;
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (!errors) {
                    // remove from the model/view
                    this.rows = this.rows.filter(r => { return r !== row;});
                    this.updateChangedFlag();
                }});
        } else {
            row._deleting = false;
        }
    }

    updateChangedRows(): Promise<void> {
        // return a promise that resolves when all changed rows are saved
        return new Promise<void>((resolve, reject) => {
            Promise.all(
                this.rows                         // users...
                    .filter(r => r._changed)      // but only the changed ones...
                    .map(r => this.updateRow(r))) // save them
                .then(()=>resolve());                   // and resolve when finished 
        });
    }
    
    updating = 0;
    updateRow(row: User): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.updating++;
            this.labbcatService.labbcat.updateUser(
                row.user, row.email, row.resetPassword, row.roles,
                (user, errors, messages) => {
                    this.updating--;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    // update the model with the field returned
                    const updatedRow = user as User;
                    const i = this.rows.findIndex(r => {
                        return r.user == updatedRow.user; })
                    this.rows[i] = updatedRow;
                    this.updateChangedFlag();
                    resolve();
                });
        });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let row of this.rows) {
            if (row._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
    }
}
