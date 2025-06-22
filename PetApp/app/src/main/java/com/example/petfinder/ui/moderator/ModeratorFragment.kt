package com.example.petfinder.ui.moderator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.petfinder.functions.AddPetDialogFragment
import com.example.petfinder.functions.RemovePetDialogFragment
import com.example.petfinder.R
import com.example.petfinder.databinding.ModeratorFragmentBinding
import com.example.petfinder.functions.CreateModeratorCodeDialogFragment
import com.example.petfinder.functions.DeleteModeratorCodeDialogFragment

class ModeratorFragment : Fragment() {

    private var _binding: ModeratorFragmentBinding? = null
    private lateinit var addPetButton: Button
    private lateinit var removePetButton: Button
    private lateinit var createModCode: Button
    private lateinit var deleteModCode: Button
    private lateinit var returnButton: Button

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModeratorFragmentBinding.inflate(inflater, container, false)

        addPetButton = binding.root.findViewById(R.id.btn_AddNewPet)
        removePetButton = binding.root.findViewById(R.id.btn_RemovePet)
        returnButton = binding.root.findViewById(R.id.btn_ReturnToAccount)
        createModCode = binding.root.findViewById(R.id.btn_CreateModeratorCode)
        deleteModCode = binding.root.findViewById(R.id.btn_DeleteModeratorCode)

        addPetButton.setOnClickListener{
            val addPetFragment = AddPetDialogFragment()
            addPetFragment.show(childFragmentManager, "addpet_dialog")
        }

        removePetButton.setOnClickListener{
            val removePetFragment = RemovePetDialogFragment()
            removePetFragment.show(childFragmentManager, "removepet_dialog")
        }

        createModCode.setOnClickListener{
            val createModCodeFragment = CreateModeratorCodeDialogFragment()
            createModCodeFragment.show(childFragmentManager, "create_mod_code_dialog")
        }

        deleteModCode.setOnClickListener{
            val deleteModCodeFragment = DeleteModeratorCodeDialogFragment()
            deleteModCodeFragment.show(childFragmentManager, "delete_mod_code_dialog")
        }

        returnButton.setOnClickListener{
            navigateToAccount()
        }

        val root: View = binding.root

        return root
    }

    private fun navigateToAccount() {
        view?.let {
            val navController = Navigation.findNavController(it)
            navController.navigate(R.id.navigation_account)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}