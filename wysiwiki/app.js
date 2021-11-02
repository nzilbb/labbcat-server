//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with LaBB-CAT; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

import InlineEditor from '@ckeditor/ckeditor5-editor-inline/src/inlineeditor';
import Essentials from '@ckeditor/ckeditor5-essentials/src/essentials';
import UploadAdapter from '@ckeditor/ckeditor5-adapter-ckfinder/src/uploadadapter';
import Autoformat from '@ckeditor/ckeditor5-autoformat/src/autoformat';
import Bold from '@ckeditor/ckeditor5-basic-styles/src/bold';
import Italic from '@ckeditor/ckeditor5-basic-styles/src/italic';
import BlockQuote from '@ckeditor/ckeditor5-block-quote/src/blockquote';
import CKFinder from '@ckeditor/ckeditor5-ckfinder/src/ckfinder';
import EasyImage from '@ckeditor/ckeditor5-easy-image/src/easyimage';
import Heading from '@ckeditor/ckeditor5-heading/src/heading';
import Image from '@ckeditor/ckeditor5-image/src/image';
import ImageCaption from '@ckeditor/ckeditor5-image/src/imagecaption';
import ImageStyle from '@ckeditor/ckeditor5-image/src/imagestyle';
import ImageToolbar from '@ckeditor/ckeditor5-image/src/imagetoolbar';
import ImageUpload from '@ckeditor/ckeditor5-image/src/imageupload';
import Indent from '@ckeditor/ckeditor5-indent/src/indent';
import Link from '@ckeditor/ckeditor5-link/src/link';
import List from '@ckeditor/ckeditor5-list/src/list';
import MediaEmbed from '@ckeditor/ckeditor5-media-embed/src/mediaembed';
import Paragraph from '@ckeditor/ckeditor5-paragraph/src/paragraph';
import PasteFromOffice from '@ckeditor/ckeditor5-paste-from-office/src/pastefromoffice';
import Table from '@ckeditor/ckeditor5-table/src/table';
import TableToolbar from '@ckeditor/ckeditor5-table/src/tabletoolbar';
import TextTransformation from '@ckeditor/ckeditor5-typing/src/texttransformation';
import CloudServices from '@ckeditor/ckeditor5-cloud-services/src/cloudservices';

import LabbcatUploadAdapter from './labbcat-upload-adapter';
//import LabbcatUtterance from './labbcat-utterance'

import Utterance from './utterance/utterance';

// InlineEditor config copied from:
// https://github.com/ckeditor/ckeditor5/blob/master/packages/ckeditor5-build-inline/src/ckeditor.js
InlineEditor.builtinPlugins = [
    Essentials,
    UploadAdapter,
    Autoformat,
    Bold,
    Italic,
    BlockQuote,
    CKFinder,
    CloudServices,
    EasyImage,
    Heading,
    Image,
    ImageCaption,
    ImageStyle,
    ImageToolbar,
    ImageUpload,
    Indent,
    Link,
    List,
    MediaEmbed,
    Paragraph,
    PasteFromOffice,
    Table,
    TableToolbar,
    TextTransformation,
    Utterance
];
// Editor configuration.
InlineEditor.defaultConfig = {
    toolbar: {
	items: [
	    'heading',
	    '|',
	    'bold',
	    'italic',
	    'link',
	    'bulletedList',
	    'numberedList',
	    '|',
	    'outdent',
	    'indent',
	    '|',
	    'uploadImage',
	    'blockQuote',
	    'insertTable',
	    'mediaEmbed',
	    'undo',
	    'redo',
            '|', 'utterance'
	]
    },
    image: {
	toolbar: [
	    'imageStyle:inline',
	    'imageStyle:block',
	    'imageStyle:side',
	    '|',
	    'toggleImageCaption',
	    'imageTextAlternative'
	]
    },
    table: {
	contentToolbar: [
	    'tableColumn',
	    'tableRow',
	    'mergeTableCells'
	]
    },
    // This value must be kept in sync with the language defined in webpack.config.js.
    language: 'en'
};

const baseUrl = document.URL.replace(/\/doc\/.*/,"/doc");
const idInMenu = document.URL.replace(/.*\/doc\//,"")
      .replace(/\//g,"→") // slashes become double-hyphens
      ||"--"; // empty string means the root page, whose id is "--"
const createButton = `<img title="Create" src="${baseUrl}/../user-interface/en/assets/add.svg">`;
const editButton = `<img title="Edit" src="${baseUrl}/../user-interface/en/assets/edit.svg">`;
const saveButton = `<img title="Save" src="${baseUrl}/../user-interface/en/assets/save.svg">`;
const deleteButton = `<img title="Delete" src="${baseUrl}/../user-interface/en/assets/delete.svg">`;

// load headers etc.
$.get(`${baseUrl}/../header`, (html, status)=>{ $("body>header").html(html); });
$.get(`${baseUrl}/../footer`, (html, status)=>{ $("body>footer").html(html); });

let canEdit = false;
function loadNavigation() {
    $.get(`${baseUrl}/index`, (html, status)=>{
        // set the nav content
        $("#main>nav").html(html);
        // ensure the current page is marked
        $(`#${idInMenu}`).addClass("current");        
        // and expand the tree view so it's visible (for all ancestors)
        let id = idInMenu;
        do { 
            $(`#${id}`).parent().attr("open", true);
            let parentId = id.replace(/→[^→]+$/,"");
            if (parentId == id) parentId = "→";
            id = parentId;
        } while (id != "→");
        // add move buttons
        if (canEdit) {
            // add ordering buttons...

            // if it's a div (a page) ...
            
            // move-up button unless it's already at the top (first-child is <summary>)
            $(`div[id='${idInMenu}']:not(:nth-child(2))`).append(
                $("<span title='Move up'>▲</span>").on("click", (e)=>{
                    move("up", $(e.target).prevAll("a").attr("href"));
                }));
            // move-down button unless it's already at the bottom
            $(`div[id='${idInMenu}']:not(:last-child)`).append(
                $("<span title='Move down'>▼</span>").on("click", (e)=>{
                    move("down", $(e.target).prevAll("a").attr("href"));
                }));

            // if it's a summary (a directory) ...
            
            // move-up button unless it's already at the top (first-child is <summary>)
            $(`details:not(:nth-child(2))>summary[id='${idInMenu}']`).append(
                $("<span title='Move up'>▲</span>").on("click", (e)=>{
                    move("up", $(e.target).prevAll("a").attr("href"));
                }));
            // move-down button unless it's already at the bottom
            $(`details:not(:last-child)>summary[id='${idInMenu}']`).append(
                $("<span title='Move down'>▼</span>").on("click", (e)=>{
                    move("down", $(e.target).prevAll("a").attr("href"));
                }));
        }
    });
}

let articleEditor = null;

$.ajax({
    type: "OPTIONS",
    url: document.URL
}).done((data, status, request)=>{
    loadNavigation();
    if (request.getResponseHeader("Allow").includes("PUT")) {
        canEdit = true;
        $("#main > aside").append(
            `<button id="edit">${editButton}</button>`);
        if ($("title").text().startsWith("*")) { // new page
            // add a default title
            const defaultTitle = decodeURIComponent(document.URL.replace(/.*\//, ""));
            $("#main>article").html(`<h2>${defaultTitle}</h2>`);
            // start editing the page immediately
            editPage();
        } else {
            $("#edit").off("click").click(editPage);
        }
    }
    if (request.getResponseHeader("Allow").includes("DELETE")) {
        $("#main > aside").append(`<button id="delete">${deleteButton}</button>`);
        $("#delete").click(deletePage);
    }
});

let creating = false;
function editPage() {
    creating = $("title").text().startsWith("*");
    if (creating) {
        if ((baseUrl+"/") == document.URL) { // creating top level page
            // add some explanatory default content
            $("#main>article").html(
                "<h2>Documentation</h2>"
                    +"<p>Add any documentation you like to this page.</p>"
                    +"<p>You can create new pages with the Link button on the tool bar above.</p>");
        }
    }
    InlineEditor.create(document.querySelector("#main>article"), {
        //toolbar:
        link: {
            // Automatically add target="_blank" and rel="noopener noreferrer" to all external links.
            addTargetToExternalLinks: true,            
        //    // Let the users control the "download" attribute of each link.
        //    decorators: [{mode: 'manual', label: 'Downloadable', attributes: {download: 'download'}}]
        }
    }).catch( error => {
        console.error( error );
    }).then((editor) => {
        editor.plugins.get( 'FileRepository' ).createUploadAdapter = ( loader ) => {
            return new LabbcatUploadAdapter( loader );
        };
        const buttonLabel = creating?createButton:saveButton;
        $("#edit")
            .html(buttonLabel)
            .off("click").click(savePage);
        editor.focus();
        articleEditor = editor;
        if (creating) {
            // hide delete button
            $("#delete").hide();
       }
    });
}

function savePage() {
    // get template
    $.get("template.html", (html, status)=> {
        
        // determine the document title
        let title = $("#main>article>h1").first().text() // first h1
            || $("#main>article>h2").first().text() // or first h2
            || document.URL.replace(/.*\//, ""); // file name by default
        title = title.replace(/^\*+/,""); // remove leading stars

        // update title in menu
        $(`#${idInMenu} > a`).html(title);
        
        // get the article content
        const article = articleEditor.getData();
        
        // insert the article content into it
        html = html
            .replace(/<article>.*<\/article>/, "<article>"+article+"</article>")
            .replace(/<title>[^<]*<\/title>/, "<title>"+title+"</title>");
        
        // save the result to the server
        $.ajax({
            type: "PUT",
            url: document.URL,
            data: html,
            contentType: "text/html"
        }).done(data=>{
            if (creating) {
                alert("Created");
                loadNavigation();
            } else {
                alert("Saved");
            }
            creating = false;
            
            // set the title locally
            $("title").text(title);
            
            // stop editing
            articleEditor.destroy();
            articleEditor = null;            
            $("#edit").html(editButton);
            $("#edit").off("click").click(editPage);

            // show delete button
            $("#delete").show();
        }).fail(r=>{
            alert(`${r.status}: ${r.statusText}\n${r.responseText}`);
        });
    });
}

function move(direction, url) {
    $.ajax({
        type: "PUT",
        url: url + "?move="+direction
    }).done(data=>{
        loadNavigation();
    });
}

function deletePage() {
    if (confirm("Are you sure you want to delete this page?")) {
        if (articleEditor) {
            articleEditor.destroy();
            articleEditor = null;
        }
        $.ajax({
            type: "DELETE",
            url: document.URL,
            contentType: "text/html"
        }).done(data=>{
            alert("Deleted");
            window.location.href = new URL(".", document.URL);
        }).fail(r=>{
            alert(`${r.status}: ${r.statusText}\n${r.responseText}`);
        });
    } // are you sure?
}

// ensure they don't accidentally navigate away without saving
$(window).on("beforeunload", function() {
    if (articleEditor) {
        return "You have not saved your changes.";
    }
});
