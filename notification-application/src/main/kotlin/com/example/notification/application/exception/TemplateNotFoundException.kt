package com.example.notification.application.exception

/** 지정한 templateKey + locale + channel 조합의 템플릿이 없음. HTTP 404. */
class TemplateNotFoundException(templateKey: String, channel: String) :
    ApplicationException("template not found: key=$templateKey channel=$channel")
