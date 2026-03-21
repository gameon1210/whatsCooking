package com.familymeal.assistant.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberProfilesViewModel @Inject constructor(
    private val memberRepository: MemberRepository
) : ViewModel() {

    val members = memberRepository.observeActiveMembers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMember(name: String, dietType: DietType, birthYear: Int?) {
        viewModelScope.launch {
            memberRepository.addMember(Member(name = name, dietType = dietType, birthYear = birthYear))
        }
    }

    fun updateMember(member: Member) {
        viewModelScope.launch { memberRepository.updateMember(member) }
    }

    fun deactivateMember(memberId: Long) {
        viewModelScope.launch { memberRepository.deactivateMember(memberId) }
    }
}
