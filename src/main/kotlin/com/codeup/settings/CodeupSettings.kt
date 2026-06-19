package com.codeup.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(name = "CodeupSettings", storages = [Storage("codeup.xml")])
@Service(Service.Level.APP)
class CodeupSettings : PersistentStateComponent<CodeupSettingsState> {
    private var myState = CodeupSettingsState()
    override fun getState(): CodeupSettingsState = myState
    override fun loadState(state: CodeupSettingsState) { myState = state }

    companion object {
        fun getInstance(): CodeupSettings = ApplicationManager.getApplication().getService(CodeupSettings::class.java)
    }
}