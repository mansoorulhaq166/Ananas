<img align="left" src="art/ananas.png" width="100" height="100" />

# Ananas Photo Editor (Fork)

[![Download](https://img.shields.io/badge/JitPack-1.2.6-blue.svg)](https://jitpack.io/#mansoorulhaq166/Ananas/1.2.6) ![API](https://img.shields.io/badge/API-16%2B-brightgreen.svg)

An easy-to-integrate photo editor for your Android apps, with custom features for your project.

## Features

- [**Paint**](#paint) with brush color, size, and eraser options.
- Adding/Editing [**Text**](#text) with the ability to change text color.
- Adding [**Stickers**](#adding-imagesstickers).
- Pinch to [**Zoom**](), [**Rotate**](), and [**Crop**]() images.
- [**Undo and Redo**](#undo-and-redo) for any changes.
- [**Saving**](#saving) edited photos.
- Applying [**Filters**]() to your image.
- Adjusting [**Contrast**]() and [**Saturation**]() of images.
- Additional [**Beauty**]() settings for face enhancement.

## Benefits
- Simple plug-and-play integration.
- Easy image editing features.

<br>

## RxJava Compatibility Note

If your project uses `RxJava 1.0`, and this library uses `RxJava 2.0`, add the following to your app’s `build.gradle` to allow both versions to co-exist:
```
android {
    packagingOptions {
        exclude 'META-INF/rxjava.properties'
    }
}
```

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
1. If started in `Portrait` mode, it stays in portrait.
2. If started in `Landscape` mode, it remains in landscape.

## How to Contribute
* Fork this project.
* Make changes and commit.
* Submit a pull request with a description of your changes.

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

 Name | Library
------------ | -------------
siwangqishiq | [ImageEditor Android](https://github.com/siwangqishiq/ImageEditor-Android)
ArthurHub | [Android Image Cropper](https://github.com/ArthurHub/Android-Image-Cropper)
hoanganhtuan95ptit | [Contrast and Brightness feature](https://github.com/hoanganhtuan95ptit/EditPhoto)
eltos | [Color Picker Dialog](https://github.com/eltos/SimpleDialogFragments)
Russell Jurney | [Kelly's 22 colors list](https://medium.com/@rjurney/kellys-22-colours-of-maximum-contrast-58edb70c90d1)
burhanrashid52 | [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor)
