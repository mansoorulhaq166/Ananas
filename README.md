# Ananas Photo Editor (Modern Rewrite)

An easy-to-integrate photo editor for your Android apps, with custom features for your project.

**Note:** This repository is a complete modern rewrite of the original [Ananas Photo Editor](https://github.com/mansoorulhaq166/Ananas). It preserves the excellent core functionality of the original library while updating the architecture and UI.

## Features

- **Paint** with brush color, size, and eraser options.
- Adding/Editing **Text** with the ability to change text color.
- Adding **Stickers** and images.
- Pinch to **Zoom**, **Rotate**, and **Crop** images.
- **Undo and Redo** for any changes.
- **Saving** edited photos.
- Applying **Filters** to your image.
- Adjusting **Contrast** and **Saturation** of images.
- Additional **Beauty** settings for face enhancement.

## Benefits

- Simple plug-and-play integration.
- Easy image editing features.
- Modernized codebase.

## Starting the PhotoEditor Activity

Define a request code in your activity:

```java
private final int PHOTO_EDITOR_REQUEST_CODE = 231; // Any integer value.
```

Launch the photo editor with the following code:

```java
try {
    Intent intent = new ImageEditorIntentBuilder(this, sourceImagePath, outputFilePath)
        .withAddText()
        .withPaintFeature()
        .withFilterFeature()
        .withRotateFeature()
        .withCropFeature()
        .withBrightnessFeature()
        .withSaturationFeature()
        .withBeautyFeature()
        .withStickerFeature()
        .forcePortrait(true)
        .setSupportActionBarVisibility(false)
        .build();
    EditImageActivity.start(activity, intent, PHOTO_EDITOR_REQUEST_CODE);
} catch (Exception e) {
    Log.e("Photo Editor", e.getMessage());
}
```

## Receiving the Output Image

Handle the result with the following code:

```java
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == PHOTO_EDITOR_REQUEST_CODE) {
        String newFilePath = data.getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH);
        boolean isImageEdit = data.getBooleanExtra(EditImageActivity.IS_IMAGE_EDITED, false);
    }
}
```

## Special Notes

The orientation will be locked when you start the editor. Once you exit, your app’s default behavior resumes:
- If started in Portrait mode, it stays in portrait.
- If started in Landscape mode, it remains in landscape.

## How to Contribute

1. Fork this project.
2. Make changes and commit.
3. Submit a pull request with a description of your changes.

Happy coding! 🎉

## Future Enhancements
- Add support for configuration changes during editing.

## Questions? 🤔
Feel free to reach out on GitHub.

## Issue Submission Guide
- Ensure you're using the latest version.
- Include details about the crash or issue, along with device type and OS version.
- Provide code snippets and crash logs.
- Be courteous and clear in your reports.

## Credits

A huge thanks to the original authors and libraries that made this possible:

| Name | Library |
| ---- | ------- |
| siwangqishiq | [ImageEditor Android](https://github.com/siwangqishiq/ImageEditor-Android) |
| ArthurHub | [Android Image Cropper](https://github.com/ArthurHub/Android-Image-Cropper) |
| hoanganhtuan95ptit | [Contrast and Brightness feature](https://github.com/hoanganhtuan95ptit/EditPhoto) |
| eltos | [Color Picker Dialog](https://github.com/eltos/SimpleDialogFragments) |
| Russell Jurney | [Kelly's 22 colors list](https://medium.com/@rjurney/kellys-22-colours-of-maximum-contrast-58edb70c90d1) |
| burhanrashid52 | [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) |
