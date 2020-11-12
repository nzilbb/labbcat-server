import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Response } from '../response';
import { User } from '../user';
import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-role-users',
  templateUrl: './admin-role-users.component.html',
  styleUrls: ['./admin-role-users.component.css']
})
export class AdminRoleUsersComponent extends AdminComponent implements OnInit {
    role_id: string;
    members: User[];
    nonmembers: User[];
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService,
        private route: ActivatedRoute
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.readRows();
    }
    
    readRows(): void {
        this.role_id = this.route.snapshot.paramMap.get('role_id');
        this.labbcatService.labbcat.readUsers(
            (users, errors, messages) => {
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                this.nonmembers = [];
                this.members = [];
                for (let user of users) {
                    if (user.roles.includes(this.role_id)) {
                        this.members.push(user as User);
                    } else {
                        this.nonmembers.push(user as User);
                    }
                } // next user                    
            });
    }

    addUser(event: any, user: User): void {
        event.target.style.left = "150%";
        event.target.style.opacity = "0";
        setTimeout(()=>{
            this.nonmembers = this.nonmembers.filter(u => { return u !== user;});
            this.members.push(user);
            user.roles.push(this.role_id);
            user._changed = this.changed = true;
        }, 250);
    }
    
    removeUser(event: any, user): void {
        event.target.style.left = "-150%";
        event.target.style.opacity = "0";
        setTimeout(()=>{
            this.members = this.members.filter(u => u !== user);
            this.nonmembers.push(user);
            user.roles = user.roles.filter(r => r !== this.role_id);
            user._changed = this.changed = true;
        }, 250);
    }

    updateChangedUsers() {
        this.members
            .filter(u => u._changed)
            .forEach(u => this.updateUser(u));
        this.nonmembers
            .filter(u => u._changed)
            .forEach(u => this.updateUser(u));
    }
    
    updating = 0;
    updateUser(user: User) {
        this.updating++;
        this.labbcatService.labbcat.updateUser(
            user.user, user.email, user.resetPassword, user.roles,
            (updated, errors, messages) => {
                this.updating--;
                if (errors) for (let message of errors) this.messageService.error(message);
                if (messages) for (let message of messages) this.messageService.info(message);
                // update the model with the field returned
                const updatedUser = updated as User;
                let i = this.members.findIndex(u => {
                    return u.user == updatedUser.user; });
                if (i >= 0) {
                    this.members[i] = updatedUser;
                } else {
                    i = this.nonmembers.findIndex(u => {
                        return u.user == updatedUser.user; });
                    if (i >= 0) {
                        this.nonmembers[i] = updatedUser;
                    }
                }
                this.updateChangedFlag();
            });
    }
    
    updateChangedFlag() {
        this.changed = false;
        for (let user of this.members) {
            if (user._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
        for (let user of this.nonmembers) {
            if (user._changed) {
                this.changed = true;
                break; // only need to find one
            }
        } // next row
    }



}
