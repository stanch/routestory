package net.routestory.model

case class Latest(total: Int, stories: List[StoryPreview])

case class Searched(total: Int, bookmark: String, stories: List[StoryPreview])

case class Tag(tag: String, count: Int)
