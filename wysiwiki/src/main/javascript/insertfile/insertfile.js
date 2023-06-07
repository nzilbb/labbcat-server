import { FileRepository } from 'ckeditor5/src/upload';
import Plugin from "@ckeditor/ckeditor5-core/src/plugin";
import ButtonView from "@ckeditor/ckeditor5-ui/src/button/buttonview";
import icon from "@ckeditor/ckeditor5-ckfinder/theme/icons/browse-files.svg";

export default class InsertFile extends Plugin {
  init() {
    const editor = this.editor;
    editor.editing.view.document.on(
      "drop",
      async (event, data) => {
        if (
          data.dataTransfer.files &&
          !data.dataTransfer.files[0].type.includes("image")
        ) {
          event.stop();
          data.preventDefault();
          this.insert(data.dataTransfer.files[0], editor);
        }
      },
      { priority: "high" }
    );

    editor.editing.view.document.on(
      "dragover",
      (event, data) => {
        event.stop();
        data.preventDefault();
      },
      { priority: "high" }
    );

    editor.ui.componentFactory.add("insertFile", (locale) => {
      const inputElement = document.createElement("input");
      inputElement.type = "file";
      inputElement.accept =
        ".doc,.docx,.pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/pdf";
      inputElement.addEventListener("change", (event) => {
        this.insert(event.target.files[0], editor);
      });

      const view = new ButtonView(locale);

      view.set({
        label: "Insert file",
        icon: icon,
        tooltip: true,
      });

      view.on("execute", () => {
        inputElement.dispatchEvent(new MouseEvent("click"));
      });

      return view;
    });
  }

    insert(file, editor) {
      if (file) {

          const fileRepository = editor.plugins.get( FileRepository );
	  const loader = fileRepository.createLoader( file );
          
          // Do not throw when upload adapter is not set. FileRepository will log an error anyway.
	  if ( !loader ) {
	      return;
	  }
          const progress = document.createElement("progress");
          progress.style.width = "100%";
          progress.max = 100;
          progress.value = 0;
          progress.title = `Uploading ${file.name}`;
          const body = document.getElementsByTagName("body")[0];
          body.appendChild(progress);
          loader.on("change:uploadedPercent", (eventInfo, name, value, oldValue)=> {
              console.log(`uploadedPercent ${value}`);
              progress.value = value;
          });
          loader.upload()
              .then( data => {
                  body.removeChild(progress);
                  this.editor.model.change( writer => {
                      const insertPosition = editor.model.document.selection.getFirstPosition();
                      writer.insertText( file.name, { linkHref: data.default }, insertPosition );
                  });
              }).catch( e => {
                  body.removeChild(progress);
	          if ( e === 'aborted' ) {
		      console.log( 'Uploading aborted.' );
	          } else {
		      alert(`Uploading error: ${e}`);
	          }
	      });
      }
    }
}
