package com.familymeal.assistant.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingMember(val name: String, val dietType: DietType, val birthYear: Int?)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val memberRepository: MemberRepository
) : ViewModel() {

    private val _pendingMembers = MutableStateFlow<List<PendingMember>>(emptyList())
    val pendingMembers: StateFlow<List<PendingMember>> = _pendingMembers

    val canProceed: StateFlow<Boolean> = _pendingMembers
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun addMember(name: String, dietType: DietType, birthYear: Int?) {
        _pendingMembers.value = _pendingMembers.value + PendingMember(name, dietType, birthYear)
    }

    fun removeMember(index: Int) {
        _pendingMembers.value = _pendingMembers.value.toMutableList().also { it.removeAt(index) }
    }

    fun completeOnboarding(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _pendingMembers.value.forEach { pending ->
                memberRepository.addMember(
                    Member(name = pending.name, dietType = pending.dietType, birthYear = pending.birthYear)
                )
            }
            onDone()
        }
    }
}
