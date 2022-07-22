import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

fun main() {
    window.onload = {

        val groovyInput = document.getElementById("groovyInput") as HTMLTextAreaElement
        val kotlinOutput = document.getElementById("kotlinOutput") as HTMLTextAreaElement

        val convert: (Event) -> dynamic = {
            kotlinOutput.textContent = GradleKotlinConverter.convert(groovyInput.value)
            Unit.asDynamic()
        }

        groovyInput.onkeyup = convert
        groovyInput.onchange = convert


        Unit.asDynamic()
    }

}
