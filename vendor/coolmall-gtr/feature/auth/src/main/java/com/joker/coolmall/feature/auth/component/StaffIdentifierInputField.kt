package com.joker.coolmall.feature.auth.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.joker.coolmall.core.designsystem.component.StartRow
import com.joker.coolmall.feature.auth.R

/**
 * Staff identifier (emp# | email | phone) — web resolve_staff_login_email input.
 */
@Composable
fun StaffIdentifierInputField(
    value: String,
    onValueChange: (String) -> Unit,
    fieldFocused: MutableState<Boolean>,
    placeholder: String = "",
    nextAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    StartRow(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = nextAction,
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { fieldFocused.value = it.isFocused },
        ) { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder.ifEmpty {
                            stringResource(id = R.string.staff_identifier_hint)
                        },
                        color = Color.Gray,
                        fontSize = 16.sp,
                    )
                }
                innerTextField()
            }
        }
    }
}
