# Ananas Photo Editor (Modern Rewrite)

An easy-to-integrate photo editor for your Android apps, completely rebuilt from the ground up using **Jetpack Compose** and **Kotlin Coroutines**.

**Note:** This repository is a modern rewrite of the original [Ananas Photo Editor](https://github.com/mansoorulhaq166/Ananas). It preserves the excellent core functionality and API of the original library while updating the entire UI and architecture to modern Android standards.

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

- **100% Jetpack Compose UI**: Fast, responsive, and easy to maintain.
- **Drop-in Replacement**: Maintains API compatibility with the original intent builder.
- **Standalone App Included**: A fully functional demo app is included to test the editor.

## Installation

Add the `photoeditor` module to your project's `settings.gradle.kts`:
```kotlin
include(":photoeditor")
```

Then add the dependency in your app's `build.gradle.kts`:
```kotlin
implementation(project(":photoeditor"))
```

## Starting the PhotoEditor Activity

Define a request code in your activity:

```kotlin
private val PHOTO_EDITOR_REQUEST_CODE = 231
```

Launch the photo editor:

```kotlin
try {
    val intent = ImageEditorIntentBuilder(this, sourceImagePath, outputFilePath)
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
        .build()
        
    EditImageActivity.start(this, intent, PHOTO_EDITOR_REQUEST_CODE)
} catch (e: Exception) {
    Log.e("Photo Editor", e.message ?: "Error launching editor")
}
```

## Receiving the Output Image

Handle the result in your Activity/Fragment:

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == PHOTO_EDITOR_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
        val newFilePath = data?.getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH)
        val isImageEdit = data?.getBooleanExtra(EditImageActivity.IS_IMAGE_EDITED, false) ?: false
        
        // Use your edited image here!
    }
}
```

## How to Contribute

1. Fork this project.
2. Make changes and commit.
3. Submit a pull request with a description of your changes.

Happy coding! 🎉

## Issue Submission Guide
- Ensure you're using the latest version.
- Include details about the crash or issue, along with device type and OS version.
- Provide code snippets and crash logs.
- Be courteous and clear in your reports.
