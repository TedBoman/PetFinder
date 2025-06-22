package com.example.petfinder.ui.search

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petfinder.R
import com.example.petfinder.database.Animal
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class AnimalAdapter(private val animalList: List<Animal>) : RecyclerView.Adapter<AnimalAdapter.AnimalViewHolder>() {

    var clickListener: AnimalClickListener? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.animal_item, parent, false)
        return AnimalViewHolder(itemView, clickListener)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val currentAnimal = animalList[position]
        holder.bind(currentAnimal)
    }

    override fun getItemCount() = animalList.size

    interface AnimalClickListener {
        fun onAnimalClicked(chipId: String)
    }

    class AnimalViewHolder(itemView: View, private val clickListener: AnimalClickListener?) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewAnimalName)
        private val birthdateTextView: TextView = itemView.findViewById(R.id.textViewAnimalAge)
        private val animalImageView: ImageView = itemView.findViewById(R.id.imageViewAnimal)

        fun bind(animal: Animal) {
            // Set the click listener
            itemView.setOnClickListener { // Use itemView for the whole layout
                val chipId = animal.chipID
                clickListener?.onAnimalClicked(chipId)
            }

            animal.birthdate?.let { timestamp ->
                val birthdate = timestamp.toDate() // Convert Timestamp to Date
                val ageText = calculateAge(birthdate)
                Log.w("AnimalViewHolder", "Loaded animal: ${animal.name} $ageText")
                birthdateTextView.text = ageText
            } ?: run {
                birthdateTextView.text = "?"
            }

            nameTextView.text = animal.name

            Glide.with(itemView.context)
                .load(animal.imageUrl)
                .into(animalImageView)
        }
        private fun calculateAge(birthdate: Date): String {
            val today = Calendar.getInstance()
            val birthdateCalendar = Calendar.getInstance().apply { time = birthdate }

            val years = today.get(Calendar.YEAR) - birthdateCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthdateCalendar.get(Calendar.DAY_OF_YEAR)) {
                val ageInYears = years - 1
                return if (ageInYears <= 0) {
                    // Calculate age in days if less than a year
                    val days = abs(birthdateCalendar.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)
                    "$days days old"
                } else {
                    "$ageInYears years old"
                }
            } else {
                return if (years <= 0) {
                    // Calculate age in days if less than a year
                    val days = abs(birthdateCalendar.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)
                    "$days days old"
                } else {
                    "$years years old"
                }
            }
        }

    }
}

