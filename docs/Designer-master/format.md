# Structure of Files

### internal:
Folder with:
1. `Board.ldes` file (see below)
2. included image files
3. included font files

##### Board.ldes file
- JSON format
- contains the root board as its root JSON element and all the styles, objects, properties and sub boards inside that

##### JSON data of all object types
a `?`behind a value means it can be `null`

1. **BaseObject** `{
"uid": Long,
"_name": String,
"_xpos": Int,
"_ypos": Int,
"_width": Int,
"_height": Int,
"_rotation": Int,
"boxStyleUID": Long?
}`

2. **SourceObject** (inherits from BaseObject) `{
"_alpha": Int
}`

3. **CopyObject** (inherits from BaseObject) `{
"sourceId": Long
}`

4. **BoardObject** (inherits from SourceObject) `{
"ObjectType": "BoardObject",
"_fonts": {"fontkey": "originalFontFileName.extension"},
"_images" {"imagekey": "originalImageFileName.extension"},
"_gridInterval": Int,
"_gridSize": Int,
"_objects": [BaseObject],
"_styles": Styles
}`
**notice:** `_fonts`, `_images` and `_styles` on sub boards are empty in the JSON file and access the parent board's property instead at runtime

5. **ImageObject** (inherits from SourceObject) `{
"_imageSource": "GOOGLE"/"IOS"/"USER"
"_src": String,
"_keepRatio": Boolean,
"_tinted": Boolean,
"_tintColor": Int,
"tintColorStyleUID": Long?
}`

6. **TextObject** (inherits from SourceObject) `{
"_text": String,
"_alignment": "ALIGN_NORMAL"/"ALIGN_OPPOSITE"/"ALIGN_CENTER",
"_textColor": Int,
"_fontSize": Int,
"_fontUID": Long,
"textStyleUID": Long?,
"textColorStyleUID": Long?
}`
**notice:** `_fontUID 0`is the default Font, Roboto Regular

7. **RectObject** (inherits from SourceObject) `{
"_fillColor": Int,
"_strokeColor": Int,
"_strokeWidth: Int,
"_corderRadius": Int,
"fillColorStyleUID": Long?,
"strokeColorStyleUID": Long?,
"_gradient": Gradient?,
"_shadow": Shadow?
}`

8. **Styles** `{
"_colorStyles": [{"_name": String, "uid": Long, "_color": Int}],
"_boxStyles": [{"_name": String, "uid": Long, "_width": Int, "_height": Int, "_cornerRadius": Int}],
"_textStyles": [{"_name": String, "uid": Long, "_fontSize": Int, "_font": Long, "_alignment": "ALIGN_NORMAL"/"ALIGN_OPPOSITE"/"ALIGN_CENTER"}]
}`

9. **Gradient** (RectObject inner class) `{
"_direction": "HORIZONTAL"/"VERTICAL"/"CIRCLE",
"_startColor": Int,
"_endColor": Int,
"startColorStyleUID": Long?,
"endColorStyleUID": Long?
}`

10. **Shadow** (RectObject inner class) `{
"_blur": Int,
"_xpos": Int,
"_ypos": Int
}`

### exported:
- zip file of files in internal folder
